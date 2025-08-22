def environmentVariablesSetup(Map config = [:]) {
    env.BUILD_USER_ID = config.BUILD_USER_ID
    env.BUILD_USER = config.BUILD_USER
    env.BUILD_USER_EMAIL = config.BUILD_USER_EMAIL
    env.JOB_NAME = config.JOB_NAME
    env.BUILD_NUMBER = config.BUILD_NUMBER
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

def hello(Map config = [:]) {
    echo "hello from ${config.who}"
    env.who = config.who
    echo "${BUILD_USER_ID} - ${env.BUILD_USER} - ${env.BUILD_USER_EMAIL} - ${env.JOB_NAME} - ${env.BUILD_NUMBER}"
}

def checkoutAndPreparation(Map config = [:]) {
    def isManual = config.BRANCHNAME_PARAM?.trim()
    if (isManual) {
        if (!config.BRANCHNAME_PARAM?.trim()) {
            error "Parameter BRANCHNAME_PARAM wajib diisi untuk manual build!"
        }
        wrap([$class: 'BuildUser']) {
            env.authorID = env.BUILD_USER_ID ?: 'Unknown'
            env.authorName = env.BUILD_USER ?: 'Unknown'
            env.authorEmail = env.BUILD_USER_EMAIL ?: 'no-reply@example.com'
        }
        env.branch_name = config.BRANCHNAME_PARAM
        echo "Manual build detected. Using branch_name from parameter: ${env.branch_name}"
        echo "Manual build by: ${env.authorName} <${env.authorEmail}>"
        MAILMESSAGE = "Your recent manual build of ${env.JOB_NAME} #${env.BUILD_NUMBER} has been started."
    } else {
        echo "Automated build from webhook detected. Using branch_name: ${env.branch_name}"
        MAILMESSAGE = """ 
Your recent commit (${env.commit_sha}) has triggered a new build of ${env.JOB_NAME} #${env.BUILD_NUMBER}.

Commit Message: 
${env.commitMessage}
"""
    }
    cleanWs()
    env.encodedJob = env.JOB_NAME.replace('/', '%2F')
    def jobParts = env.JOB_NAME.tokenize('/')
    env.jobDisplay = jobParts[1]
    echo "This Build is Triggered by ${env.authorName} (${env.authorEmail})"
    echo "Blue Ocean URL yang disiapkan:"
    echo "${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline"
    // Notify Build Has Started via Email 
    emailext(
        attachLog: true,
        subject: "Jenkins Pipeline Started - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
        body: """ 
Hello ${env.authorName}, 

${MAILMESSAGE}

You can follow the progress here:
${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

Thank you.

Best regards,  
DevOps Team
""",
        to: """\
                            ${env.authorEmail},
                            cc:${env.developersEmail},
                            bcc:${env.bccEmail1},
                            bcc:${env.bccEmail2}
                        """
    )
    if (env.branch_name == "master" || env.branch_name == "main" || env.branch_name == "develop") {
        env.sonarProjectKey = "${env.appName}-${env.branch_name.toLowerCase()}"
    } else if (env.branch_name.startsWith("release/") || env.branch_name.startsWith("hotfix/")) {
        env.sonarProjectKey = "${env.appName}-${env.branch_name.split('/')[0].toLowerCase()}"
    } else {
        // For other branches, use full branch name with compliance formatting
        env.sonarProjectKey = "${env.appName}-${env.branch_name.toLowerCase().replaceAll('[^a-z0-9-]', '-')}"
    }
    // Set up image name based on environment (if not production (master/main) branches, will have suffix -dev)
    if (env.branch_name == "master" || env.branch_name == "main") {
        env.imageName = "${env.imageNameBase}"
    } else {
        env.imageName = "${env.imageNameBase}-dev"
    }
    // Set up image tag based on branch
    if ((env.branch_name == "master" || env.branch_name == "main" || env.branch_name.startsWith("release/") 
        || env.branch_name.startsWith("hotfix/") || env.branch_name == "develop")) {
        env.imageTag = "v1.${env.BUILD_NUMBER}"
    } else {
        env.imageTag = "${env.commit_sha}"
    }
    echo "Using SonarQube project key: ${env.sonarProjectKey}"
    echo "Using Image Name: ${env.imageName}"
    echo "Using Image Tag: ${env.imageTag}"
    git branch: env.branch_name, credentialsId: env.gitCredentials, url: env.gitUrl
}