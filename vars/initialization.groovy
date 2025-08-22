def hello(Map config = [:]) {
    echo "hello from ${config.who}"
    env.who = config.who
}

def preparation(Map config = [:]) {
    def isManual = config.BRANCHNAME_PARAM?.trim()
    if (isManual) {
        if (!config.BRANCHNAME_PARAM?.trim()) {
            error "Parameter BRANCHNAME_PARAM wajib diisi untuk manual build!"
        }
        wrap([$class: 'BuildUser']) {
            authorID = config.BUILD_USER_ID ?: 'Unknown'
            authorName = config.BUILD_USER ?: 'Unknown'
            authorEmail = config.BUILD_USER_EMAIL ?: 'no-reply@example.com'
        }
        branch_name = config.BRANCHNAME_PARAM
        echo "Manual build detected. Using branch_name from parameter: ${branch_name}"
        echo "Manual build by: ${authorName} <${authorEmail}>"
        MAILMESSAGE = "Your recent manual build of ${config.JOB_NAME} #${config.BUILD_NUMBER} has been started."
    } else {
        echo "Automated build from webhook detected. Using branch_name: ${config.branch_name}"
        MAILMESSAGE = """ 
Your recent commit (${config.commit_sha}) has triggered a new build of ${config.JOB_NAME} #${config.BUILD_NUMBER}.

Commit Message: 
${config.commitMessage}
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