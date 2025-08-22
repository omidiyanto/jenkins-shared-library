def environmentVariablesSetup(Map config = [:]) {
    echo "Setting up environment variables for shared library usage..."
    env.BUILD_USER_ID = config.BUILD_USER_ID
    env.BUILD_USER = config.BUILD_USER
    env.BUILD_USER_EMAIL = config.BUILD_USER_EMAIL
    env.JOB_NAME = config.JOB_NAME
    env.BUILD_NUMBER = config.BUILD_NUMBER
    env.branch_name = config.branch_name
    env.commit_sha = config.commit_sha
    env.commitMessage = config.commitMessage
    env.gitUrl = config.gitUrl
    env.gitOpsRepo = config.gitOpsRepo
    env.imageNameBase = config.imageNameBase
    env.registryName = config.registryName
    env.djangoSecretKey = config.djangoSecretKey
    env.appName = config.appName
    env.firstApproverID = config.firstApproverID
    env.firstApproverEmail = config.firstApproverEmail
    env.secondApproverID = config.secondApproverID
    env.secondApproverEmail = config.secondApproverEmail
    env.developersEmail = config.developersEmail
    env.bccEmail1 = config.bccEmail1
    env.bccEmail2 = config.bccEmail2
}

def printEnvVars(Map config = [:]) {
    echo "BUILD_USER_ID: ${env.BUILD_USER_ID}"
    echo "BUILD_USER: ${env.BUILD_USER}"
    echo "BUILD_USER_EMAIL: ${env.BUILD_USER_EMAIL}"
    echo "JOB_NAME: ${env.JOB_NAME}"
    echo "BUILD_NUMBER: ${env.BUILD_NUMBER}"
    echo "branch_name: ${env.branch_name}"
    echo "commit_sha: ${env.commit_sha}"
    echo "commitMessage: ${env.commitMessage}"
    echo "gitUrl: ${env.gitUrl}"
    echo "gitOpsRepo: ${env.gitOpsRepo}"
    echo "imageNameBase: ${env.imageNameBase}"
    echo "registryName: ${env.registryName}"
    echo "djangoSecretKey: ${env.djangoSecretKey}"
    echo "appName: ${env.appName}"
    echo "firstApproverID: ${env.firstApproverID}"
    echo "firstApproverEmail: ${env.firstApproverEmail}"
    echo "secondApproverID: ${env.secondApproverID}"
    echo "secondApproverEmail: ${env.secondApproverEmail}"
    echo "developersEmail: ${env.developersEmail}"
    echo "bccEmail1: ${env.bccEmail1}"
    echo "bccEmail2: ${env.bccEmail2}"
}   