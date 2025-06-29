import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand

project {
    buildType(BuildAndPushImage)
}

object BuildAndPushImage : BuildType({
    name = "Build and Push Docker Image"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = """
            +:refs/heads/main
            +:refs/tags/*
        """.trimIndent()
    }

    steps {
        // Установить yc CLI и авторизоваться
        script {
            name = "Setup YC CLI and Login to Yandex Container Registry"
            scriptContent = """
                # Авторизация через ключ сервисного аккаунта
                yc config set service-account-key %home.path%/secrets/registry_sa_key.json

                # Получаем токен IAM для Docker login
                TOKEN=\$(yc iam create-token)

                # Авторизация в Container Registry
                docker login cr.yandex/%REGISTRY_ID% --username iam --password \$TOKEN
            """.trimIndent()
        }

        // Определяем тег
        script {
            name = "Determine Docker Tag"
            scriptContent = """
                if [[ "%teamcity.build.vcs.branch%" == release/* || "%teamcity.build.vcs.branch%" == refs/tags/* ]]; then
                    TAG=\$(echo "%teamcity.build.vcs.branch%" | sed -e 's|refs/tags/||' -e 's|release/||')
                    echo "##teamcity[setParameter name='docker.tag' value='\$TAG']"
                else
                    TAG=\$(git rev-parse --short HEAD)
                    echo "##teamcity[setParameter name='docker.tag' value='\$TAG']"
                fi
            """.trimIndent()
        }

        // Сборка образа
        dockerCommand {
            name = "Build Docker Image"
            commandType = build {
                namesAndTags = "cr.yandex/%REGISTRY_ID%/my-app:%docker.tag%"
                platform = "linux/amd64"
                contextDir = "."
            }
        }

        // Пуш образа
        dockerCommand {
            name = "Push Docker Image"
            commandType = push {
                imageTag = "cr.yandex/%REGISTRY_ID%/my-app:%docker.tag%"
            }
        }
    }

    requirements {
        requirement("docker", "present")
    }

    params {
        param("home.path", "/home/teamcity") // или другой путь к домашней директории
        param("env.REGISTRY_ID", "") // Передается как параметр настройки проекта
        password("env.REGISTRY_SA_KEY_PATH", "credentialsJSON:saKey") // секретный файл
    }

    triggers {
        vcs {
            branchFilter = """
                +:refs/heads/main
                +:refs/tags/*
            """.trimIndent()
        }
    }

    features {
        feature {
            type = "commit-status-publisher"
            param("publisherId", "github")
        }
    }
})