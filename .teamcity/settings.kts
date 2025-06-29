import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.docker.*

project {
    buildType(BuildAndPushImage)
}

object BuildAndPushImage : BuildType({
    name = "Build and Push Docker Image"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = """
            +:refs/heads/main
        """.trimIndent()
    }

    steps {
        script {
            name = "Setup YC CLI and Login to Yandex Container Registry"
            scriptContent = """
                yc config set service-account-key %home.path%/secrets/registry_sa_key.json
                TOKEN=\$(yc iam create-token)
                docker login cr.yandex/%REGISTRY_ID% --username iam --password \$TOKEN
            """.trimIndent()
        }

        script {
            name = "Determine Docker Tag"
            scriptContent = """
                TAG=\$(git describe --tags --exact-match 2>/dev/null || echo "")
                if [ -n "\$TAG" ]; then
                    echo "##teamcity[setParameter name='docker.tag' value='\$TAG']"
                else
                    COMMIT_HASH=\$(git rev-parse --short HEAD)
                    echo "##teamcity[setParameter name='docker.tag' value='\$COMMIT_HASH']"
                fi
            """.trimIndent()
        }

        dockerBuild {
            name = "Build Docker Image"
            param("imageTag", "cr.yandex/%REGISTRY_ID%/my-app:%docker.tag%")
            contextDir = "."
        }

        dockerPush {
            name = "Push Docker Image"
            param("imageTag", "cr.yandex/%REGISTRY_ID%/my-app:%docker.tag%")
        }
    }

    requirements {
        dockerSupport {
            filter = "docker"
        }
    }

    params {
        param("home.path", "/home/teamcity")
        param("env.REGISTRY_ID", "")
        password("env.REGISTRY_SA_KEY_PATH", "credentialsJSON:saKey")
    }

    triggers {
        vcs {
            branchFilter = """
                +:refs/heads/main
            """.trimIndent()
        }
    }

    features {
        commitStatusPublisher {
            publisherId = "github"
        }
    }
})