import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

project {
    id("NotesAppProject")
    name = "Notes App"
    description = "CI/CD pipeline for Notes App with Docker and Helm"

    // Подключение VCS
    vcsRoot(GitHubRepo)

    // Конфигурации билдов
    buildType(BuildAndPushDockerImage)
    buildType(DeployToKubernetes)
}

object GitHubRepo : GitVcsRoot({
    name = "GitHub Repo"
    url = "https://github.com/ <your-username>/<your-repo>.git"
    branchSpec = "+:refs/heads/main; +:refs/tags/*"
    authMethod = password {
        userName = "your-git-user"
        password = "env.GIT_PASSWORD" // добавить в параметры TeamCity
    }
})

object BuildAndPushDockerImage : BuildType({
    name = "Build and Push Docker Image"
    description = "Builds Docker image and pushes to Yandex Container Registry"

    vcs {
        root(Refs.all)
    }

    params {
        param("env.REGISTRY_ID", "credentialsJSON") // ID YC Registry
        password("env.NOTES_APP_POSTGRESQL_USER", "credentialsJSON") // Dummy, нужно заменить
        password("env.NOTES_APP_POSTGRESQL_PASSWORD", "credentialsJSON") // Dummy, нужно заменить
    }

    steps {
        step("Detect Branch Type", StepModelType.Script) {
            scriptContent = """
                #!/bin/bash
                set -e
                
                if [[ "%build.vcs.number%" == refs/tags/* ]]; then
                    echo "##teamcity[setParameter name='env.IS_TAG' value='true']"
                    echo "##teamcity[setParameter name='env.RELEASE_TAG' value='${'$'}{build.vcs.number##*/}]"
                else
                    echo "##teamcity[setParameter name='env.IS_TAG' value='false']"
                    COMMIT_HASH=$(git rev-parse --short HEAD)
                    echo "##teamcity[setParameter name='env.COMMIT_HASH' value='${'$'}COMMIT_HASH]"
                fi
            """.trimIndent()
        }

        script {
            name = "Login to Yandex Container Registry"
            scriptContent = """
                yc config set service-account-key /home/teamcity/sa-key.json
                IAM_TOKEN=$(yc iam create-token)
                docker login --username iam --password ${'$'}IAM_TOKEN cr.yandex/%env.REGISTRY_ID%
            """
        }

        script {
            name = "Build and Push Docker Image"
            scriptContent = """
                if [ "%env.IS_TAG%" = "true" ]; then
                    docker build -t cr.yandex/%env.REGISTRY_ID%/notes-app:%env.RELEASE_TAG% .
                    docker push cr.yandex/%env.REGISTRY_ID%/notes-app:%env.RELEASE_TAG%
                else
                    docker build -t cr.yandex/%env.REGISTRY_ID%/notes-app:%env.COMMIT_HASH% .
                    docker push cr.yandex/%env.REGISTRY_ID%/notes-app:%env.COMMIT_HASH%
                fi
            """
        }
    }

    triggers {
        vcs {
            branchFilter = "+:<default>"
        }
    }
})

object DeployToKubernetes : BuildType({
    name = "Deploy to Kubernetes"
    description = "Deploys the app to Kubernetes using Helm on tag push"

    vcs {
        root(Refs.all)
    }

    steps {
        script {
            name = "Helm Upgrade"
            scriptContent = """
                helm upgrade --kubeconfig /home/teamcity/kube_config \
                    --install notes-app oci://registry-1.docker.io/torrmund/notes-app --version 0.1.0 \
                    -f /home/teamcity/values.yaml \
                    --set "database.user=%NOTES_APP_POSTGRESQL_USER%" \
                    --set "database.password=%NOTES_APP_POSTGRESQL_PASSWORD%" \
                    --set "image.tag=%env.RELEASE_TAG%" \
                    --atomic
            """
        }
    }

    triggers {
        vcs {
            branchFilter = "+:refs/tags/*"
        }
    }

    dependencies {
        dependency(BuildAndPushDockerImage) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }
})