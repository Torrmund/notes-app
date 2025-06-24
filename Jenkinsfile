pipeline {
    agent any
    triggers{
        githubPush()
    }
    environment {
        YCR_ID = credentials('ycr-id')
        IMAGE_NAME = 'notes-app'

        // Получаем хэш последнего коммита
        COMMIT_HASH = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        // Формируем имя образа
        IMAGE_TAG = "${IMAGE_NAME}:${COMMIT_HASH}"
    }

    stages{
        stage('Build Docker Image') {
            steps {
                echo "Building Docker image with tag: ${IMAGE_TAG}"
                sh "docker build -t cr.yandex/${YCR_ID}/${IMAGE_TAG} ."
            }
        }

        stage("Push Docker Image to Yandex Container Registry") {
            steps {
                script{
                    '''
                    #!/bin/bash
                    echo "Logging in to Yandex Container Registry"
                    yc config set service-account-key ${env.HOME}/secrets/registry_sa_key.json
                    docker login --username iam --password "$(yc iam create-token)" cr.yandex/${env.YCR_ID}
                    echo "Pushing Docker image with tag: ${IMAGE_TAG}"
                    docker push cr.yandex/${YCR_ID}/${IMAGE_TAG}
                    '''
                }
            }
        }
    }
}