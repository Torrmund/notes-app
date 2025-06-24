pipeline {
    agent any

    triggers{
        githubPush()
    }

    parameters{
        string(name: 'REGISTRY_ID', defaultValue: '', description 'Идентификатор Yandex Container Registry')
    }

    environment {
        REGISTRY_URL = "cr.yandex/${params.REGISTRY_ID}"
        REGISTRY_SA_KEY_PATH = '/var/lib/jenkins/secrets/registry_sa_key.json'
    }

    stages{
        stage('Get Git Commit Hash'){
            steps {
                script {
                    // Получаем хеш последнего коммита
                    def COMMIT_HASH = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.COMMIT_HASH = COMMIT_HASH
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
                echo 'Сборка и публикация Docker образа...'
                sh """
                    docker build -t ${REGISTRY_URL}/notes-app:${env.COMMIT_HASH} .
                    docker push ${REGISTRY_URL}/notes-app:${env.COMMIT_HASH}
                """
            }
        }
    }
}

post {
    success {
        echo "Docker image successfully built and pushed to ${REGISTRY_URL}/notes-app:${env.COMMIT_HASH}"
    }
    failure {
        echo "An error occurred while executing the pipeline."
    }
}