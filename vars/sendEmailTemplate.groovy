/**
 * Sends a standardized email notification based on the specified mode.
 *
 * @param config A map containing the notification configuration.
 * @param config.MAILMODE (Required) The type of email to send.
 * Valid modes: 'START_AUTOMATED', 'START_MANUAL', 'APPROVAL_1', 'APPROVAL_2',
 * 'UAT_UNHEALTHY', 'PROD_UNHEALTHY', 'POST_BUILD_REPORT'.
 * @param config.RECIPIENT_EMAIL (Optional) The primary recipient's email address. Defaults to the build author.
 * @param config.RECIPIENT_NAME (Optional) The primary recipient's name for personalization.
 * @param config.ATTACHMENT_PATTERN (Optional) A pattern for files to attach (e.g., 'report.html').
 * @param config.EXTRA_DATA (Optional) A map for any additional data required by specific templates.
 * - For APPROVAL_2: [firstApprover: 'User Name']
 * - For UAT_UNHEALTHY: [healthStatus: 'Degraded']
 * - For PROD_UNHEALTHY: [unhealthyList: 'dept-A, dept-B', healthyList: 'dept-C']
 * - For POST_BUILD_REPORT: [buildResult: 'SUCCESS']
 */
def call(Map config = [:]) {
    // --- 1. Declare variables for the email components ---
    def emailSubject
    def emailBody
    
    // Default recipient is the user who triggered the build
    def primaryRecipientEmail = config.RECIPIENT_EMAIL ?: env.authorEmail
    def primaryRecipientName = config.RECIPIENT_NAME ?: 'Team'

    // --- 2. Build Subject and Body based on MAILMODE ---
    switch (config.MAILMODE) {
        case 'START_AUTOMATED':
            emailSubject = "[STARTED] CI/CD Pipeline for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
Your recent commit (${env.commit_sha}) has triggered a new build.

Commit Message:
${env.commitMessage}
"""
            break

        case 'START_MANUAL':
            emailSubject = "[STARTED] CI/CD Pipeline for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = "Your manual build of ${env.JOB_NAME} #${env.BUILD_NUMBER} has been started."
            break

        case 'APPROVAL_1':
            emailSubject = "[APPROVAL] CI/CD Production Deployment - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
A deployment to the Production environment has been initiated and requires your approval.

The pipeline is waiting for your decision to proceed. Please access the Jenkins pipeline via the link below to approve or reject the deployment.
"""
            break

        case 'APPROVAL_2':
            def firstApproverName = config.EXTRA_DATA?.firstApprover ?: 'The first approver'
            emailSubject = "[APPROVAL] CI/CD Production Deployment - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The first approval for the production deployment was completed by ${firstApproverName}.

Your final approval is now required to proceed. Please access the Jenkins pipeline via the link below to provide your decision.
"""
            break

        case 'UAT_UNHEALTHY':
            def healthStatus = config.EXTRA_DATA?.healthStatus ?: 'Unknown'
            emailSubject = "[FAILURE] CI/CD Pipeline Failed for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The pipeline has FAILED because the application '${env.argoAppName}' in the UAT environment is not healthy by ArgoCD health check.

Current Status: ${healthStatus}

Please check the deployment and application logs for more details.
"""
            break
            
        case 'PROD_UNHEALTHY':
            def unhealthyDepts = config.EXTRA_DATA?.unhealthyList ?: 'N/A'
            def healthyDepts = config.EXTRA_DATA?.healthyList ?: 'N/A'
            emailSubject = "[FAILURE] CI/CD Production Deployment Failed for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The production deployment has FAILED because one or more deployments of application did not become healthy after syncing by ArgoCD health check.

Unhealthy Departments: ${unhealthyDepts}
Healthy Departments: ${healthyDepts}

Please check the deployment for immediate investigation.
"""
            break

        case 'POST_BUILD_REPORT':
            def buildResult = config.EXTRA_DATA?.buildResult ?: 'UNKNOWN'
            emailSubject = "[REPORT] CI/CD Pipeline ${buildResult} - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The CI/CD pipeline has completed with the result: ${buildResult}.

Please review the build, deployment, and logs for further details.
"""
            break

        default:
            error("Invalid MAILMODE '${config.MAILMODE}' provided to the mail function.")
            break
    }

    // --- 3. Construct the final email body with common header/footer ---
    def finalBody = """
Hello ${primaryRecipientName},

${emailBody}

Pipeline URL and Details:
${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

Thank you.

Best regards,  
DevSecOps Team
"""

    // --- 4. Send the email using the constructed components ---
    emailext(
        attachLog: true,
        subject: emailSubject,
        body: finalBody,
        attachmentsPattern: config.ATTACHMENT_PATTERN ?: '', // Use Elvis operator for a safe default
        to: """\
            ${primaryRecipientEmail},
            cc:${env.developersEmail},
            bcc:${env.bccEmail1},
            bcc:${env.bccEmail2}
        """
    )
}