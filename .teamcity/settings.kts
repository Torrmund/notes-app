import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2025.03"

project {
    description = "CI/CD pipeline for Notes App with Docker and Helm"

    vcsRoot(NotesApp)

    buildType(Build)
    buildType(Deploy)
}

object Build : BuildType({
    name = "Build"

    params {
        param("env.REGISTRY_ID", "crpakasq8iqpp234ar6d")
        param("docker.tag", "latest")
        param("is.tag", "")
    }

    vcs {
        root(NotesApp)
    }

    steps {
        script {
            name = "Login to YCR"
            id = "Login_to_YCR"
            scriptContent = """
                #!/bin/bash
                set -e
                yc config set service-account-key %teamcity.agent.home.dir%/secrets/registry_sa_key.json
                IAM_TOKEN=${'$'}(yc iam create-token)
                docker login --username iam --password ${'$'}IAM_TOKEN cr.yandex/%env.REGISTRY_ID%
            """.trimIndent()
        }
        script {
            name = "Determine Docker Tag"
            id = "Determine_Docker_Tag"
            scriptContent = """
                #!/bin/bash
                set -e
                TAG=${'$'}(git describe --tags --exact-match 2>/dev/null || echo "")
                if [ -n "${'$'}TAG" ]; then
                	echo "##teamcity[setParameter name='docker.tag' value='${'$'}TAG']"
                    echo "##teamcity[setParameter name='is.tag' value='true']"
                else
                	COMMIT_HASH=${'$'}(git rev-parse --short HEAD)
                    echo "##teamcity[setParameter name='docker.tag' value='${'$'}COMMIT_HASH']"
                fi
            """.trimIndent()
        }
        dockerCommand {
            name = "Docker Build"
            id = "Docker_Build"
            commandType = build {
                source = file {
                    path = "./Dockerfile"
                }
                platform = DockerCommandStep.ImagePlatform.Linux
                namesAndTags = "cr.yandex/%env.REGISTRY_ID%/notes-app:%docker.tag%"
            }
        }
        dockerCommand {
            name = "Docker Push"
            id = "Docker_Push"
            commandType = push {
                namesAndTags = "cr.yandex/%env.REGISTRY_ID%/notes-app:%docker.tag%"
            }
        }
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main, +:refs/tags/*"
        }
    }
})

object Deploy : BuildType({
    name = "Deploy"

    params {
        password("env.NOTES_APP_POSTGRESQL_PASSWORD", "zxxd088021ef214bbf0f9a3ee1583bb2b563a783e939dd6507c")
        password("env.NOTES_APP_POSTGRESQL_USER", "zxxd088021ef214bbf0d18887d4e467e74e")
    }

    vcs {
        root(NotesApp)
    }

    steps {
        script {
            name = "Helm Deploy"
            id = "Helm_Deploy"
            scriptContent = """
                #!/bin/bash
                set -e
                helm upgrade --kubeconfig %teamcity.agent.home.dir%/secrets/kube_config \
                	--install notes-app oci://registry-1.docker.io/torrmund/notes-app --version 0.1.0 \
                    -f %teamcity.agent.home.dir%/secrets/notes_app_values.yaml \
                    --set "database.user=%env.NOTES_APP_POSTGRESQL_USER%" \
                    --set "database.password=%env.NOTES_APP_POSTGRESQL_PASSWORD%" \
                   	--set "image.tag=${Build.depParamRefs["docker.tag"]}" \
                    --atomic
            """.trimIndent()
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "${Build.id}"
            successfulOnly = true
            branchFilter = "+:refs/tags/v*"
        }
    }

    dependencies {
        snapshot(Build) {
            onDependencyFailure = FailureAction.CANCEL
        }
    }
})

object NotesApp : GitVcsRoot({
    name = "NotesApp"
    url = "git@github.com:Torrmund/notes-app.git"
    branch = "refs/heads/main"
    branchSpec = "+:refs/heads/main, +:refs/tags/*"
    authMethod = uploadedKey {
        uploadedKey = "id_ed25519"
    }
})