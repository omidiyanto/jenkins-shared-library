def loadEnvVars() {
    // Jenkins user IDs yang diperbolehkan untuk approve
    env.firstApproverID = 'omidiyantosatnusa'
    env.firstApproverEmail = 'o.midiyanto@satnusa.com'
    env.secondApproverID = 'omidiyanto7'
    env.secondApproverEmail = 'omidiyanto7@gmail.com'
    env.developersEmail = 'o.midiyanto@satnusa.com'
    env.bccEmail1= 'omidiyanto7@gmail.com'
    env.bccEmail2= 'omidiyanto7@gmail.com'   
    env.registryUsername = 'developer'
    env.argocdServer = '192.168.88.20:30275' // URL ArgoCD server
    env.gitCredentials = 'bitbucket-satnusa-account' //buat di credentials Jenkins
    env.SONAR_TOKEN_NAME = "sonarqube-token"
    env.SONAR_HOST_URL = 'http://192.168.88.20:9000'
    env.DD_API_KEY_NAME = 'dd-api-key-live'
    env.DD_URL = 'http://192.168.88.20:8380'
}

def checkoutAndPreparation() {
    loadEnvVars()
    // Validasi trigger: manual (parameter) atau webhook
    def isManual = params.BRANCHNAME_PARAM?.trim()
    if (isManual) {
        if (!params.BRANCHNAME_PARAM?.trim()) {
            error "Parameter BRANCHNAME_PARAM wajib diisi untuk manual build!"
        }
        wrap([$class: 'BuildUser']) {
            env.authorID = env.BUILD_USER_ID ?: 'Unknown'
            env.authorName = env.BUILD_USER ?: 'Unknown'
            env.authorEmail = env.BUILD_USER_EMAIL ?: 'no-reply@example.com'
        }
        env.branch_name = params.BRANCHNAME_PARAM
        echo "Manual build detected. Using branch_name from parameter: ${env.branch_name}"
        echo "Manual build by: ${env.authorName} <${env.authorEmail}>"
        MAILMODE="START_MANUAL"
    } else {
        echo "Automated build from webhook detected. Using branch_name: ${env.branch_name}"
        MAILMODE="START_AUTOMATED"
    }
    cleanWs()
    env.encodedJob = env.JOB_NAME.replace('/', '%2F')
    def jobParts = env.JOB_NAME.tokenize('/')
    env.jobDisplay = jobParts[1]
    echo "This Build is Triggered by ${env.authorName} (${env.authorEmail})"
    echo "Blue Ocean URL yang disiapkan:"
    echo "${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline"
    // Notify Build Has Started via Email 
    sendEmailTemplate(MAILMODE: MAILMODE, RECIPIENT_NAME: env.authorName)

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

def postAction(Map config = [:]) {
    sh "docker rm -f ${env.appName}-sca-test-${env.BUILD_NUMBER}"
    sh "docker rmi -f ${env.registryName}/${env.imageName}:${env.imageTag}"
    sh "docker rmi -f ${env.registryName}/${env.imageName}:${env.commit_sha}"
    sh "docker rmi -f ${env.appName}-sca-test-${env.BUILD_NUMBER}"
    sh "docker volume rm ${env.appName}-sca-test-${env.BUILD_NUMBER}-volume"
    sh "rm -rf ~/${env.BUILD_NUMBER}-${env.appName}-unit-test"
    sh "docker rmi -f ${env.registryName}/${env.imageName}:${env.imageTag}-unit-test"
    sh "PGPASSWORD=postgres psql -U postgres -h jenkins-postgres -p 5432 -c 'DROP DATABASE IF EXISTS nusames_analytic_${env.BUILD_NUMBER};'"
    def ddCustomPolicy = [
        'Trufflehog Scan': true,
        'Anchore Grype'  : false,
        'Trivy Scan'     : false,
        'SonarQube Scan' : false
    ]
    securityTest.generateReportAndPublishToDD(verifiedPolicy: ddCustomPolicy)
    // Notify Build Has Finished via Email
    def buildResult = currentBuild.result
    sendEmailTemplate(MAILMODE: 'POST_BUILD_REPORT',RECIPIENT_NAME: env.authorName,EXTRA_DATA: [buildResult: buildResult],ATTACHMENT_PATTERN: 'report.html')
    cleanWs()
}

