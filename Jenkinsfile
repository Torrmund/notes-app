pipeline {
    agent any

    triggers{
        GenericTrigger(
            genericVariables: [
                [key: 'ref', value: '$.ref'],
                [key: 'before', value: '$.before'],
                [key: 'after', value: '$.after'],
                [key: 'repository', value: '$.repository.name']
            ],
            causeString: 'GitHub Push Event',
            triggerConditions: [
                new BooleanTriggerCondition().setExpression('ref ==~ /refs/(heads\\/main|tags\\/)/')
            ],
            printContributedVariables: true,
            printPostContent: true,
            silentMode: false
        )
    }

    parameters{
        string(name: 'REGISTRY_ID', defaultValue: 'crp2d7g0dbcns6rmgje3', description: 'Идентификатор Yandex Container Registry')
    }

    environment {
        REGISTRY_URL = "cr.yandex/${params.REGISTRY_ID}"
        REGISTRY_SA_KEY_PATH = '/var/lib/jenkins/secrets/registry_sa_key.json'

        // Путь к values для Helm
        NOTES_APP_VALUES = '/var/lib/jenkins/secrets/notes_app_values.yaml'

        // Путь к kubeconfig
        KUBECONFIG_PATH = '/var/lib/jenkins/secrets/kube_config'

        // Секреты для деплоя приложения
        NOTES_APP_POSTGRESQL_USER = credentials('NOTES_APP_POSTGRESQL_USER')
        NOTES_APP_POSTGRESQL_PASSWORD = credentials('NOTES_APP_POSTGRESQL_PASSWORD')

        // Определяем тип запуска
        IS_TAG = sh(script: 'if [ -n "$GIT_TAG" ]; then echo "true"; else echo "false"; fi', returnStdout: true).trim()
        COMMIT_HASH = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }

    stages{
        stage('Determine Build Type') {
            steps {
                script {
                    if (env.IS_TAG == 'true') {
                        echo "Запущена сборка релиза по тегу: ${env.GIT_TAG}"
                        env.RELEASE_TAG = env.GIT_TAG
                    } else {
                        echo "Запущена сборка по коммиту: ${env.COMMIT_HASH}"
                    }
                }
            }
        }

        stage('Login to Yandex Container Registry') {
            steps {
                echo "Logging in to Yandex Container Registry..."

                script {
                    // Настройка YC CLI
                    sh """
                        yc config set service-account-key ${REGISTRY_SA_KEY_PATH}
                    """

                    // Получем токен
                    def IAM_TOKEN = sh(
                        script: "yc iam create-token",
                        returnStdout: true
                    ).trim()

                    // Логинимся в Yandex Container Registry
                    sh """
                        docker login --username iam --password ${IAM_TOKEN} ${REGISTRY_URL}
                    """
                }
            }
        }

        stage('Build/Push Docker Image') {
            steps {
                script {
                    if (env.IS_TAG == "true") {
                        echo "Сборка и публикация релизного образа с тегом: ${env.RELEASE_TAG}"
                        sh """
                            docker build -t ${REGISTRY_URL}/notes-app:${env.RELEASE_TAG} .
                            docker push ${REGISTRY_URL}/notes-app:${env.RELEASE_TAG}
                        """
                    } else {
                        echo "Сборка и публикация образа с хешем коммита: ${env.COMMIT_HASH}"
                        sh """
                            docker build -t ${REGISTRY_URL}/notes-app:${env.COMMIT_HASH} .
                            docker push ${REGISTRY_URL}/notes-app:${env.COMMIT_HASH}
                        """
                    }
                }
            }
        }

        stage('Deploy to K8s with Helm') {
            when{
                expression { env.IS_TAG == 'true' }
            }
            steps {
                echo "Deploying to Kubernetes using Helm..."
                sh '''
                    helm upgrade --kubeconfig ${KUBECONFIG_PATH} \
                        --install notes-app oci://registry-1.docker.io/torrmund/notes-app --version 0.1.0 \
                        -f ${NOTES_APP_VALUES} \
                        --set "database.user=${NOTES_APP_POSTGRESQL_USER}" \
                        --set "database.password=${NOTES_APP_POSTGRESQL_PASSWORD}" \
                        --set "image.tag=${env.RELEASE_TAG}" \
                        --atomic
                    '''
            }
        }
    }

    post {
        success {
            script {
                if (env.IS_TAG == 'true') {
                    echo "Pipeline completed successfully for release tag: ${env.RELEASE_TAG}"
                } else {
                    echo "Pipeline completed successfully for commit hash: ${env.COMMIT_HASH}"
                }
            }
        }
        failure {
            echo "An error occurred while executing the pipeline."
        }
    }
}