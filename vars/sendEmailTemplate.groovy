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
def mail(Map config = [:]) {
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
            emailSubject = "[APPROVAL] Production Deployment - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
A deployment to the Production environment has been initiated and requires your approval.

The pipeline is waiting for your decision to proceed. Please access the Jenkins pipeline via the link below to approve or reject the deployment.
"""
            break

        case 'APPROVAL_2':
            def firstApproverName = config.EXTRA_DATA?.firstApprover ?: 'The first approver'
            emailSubject = "[APPROVAL] Production Deployment - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The first approval for the production deployment was completed by ${firstApproverName}.

Your final approval is now required to proceed. Please access the Jenkins pipeline via the link below to provide your decision.
"""
            break

        case 'UAT_UNHEALTHY':
            def healthStatus = config.EXTRA_DATA?.healthStatus ?: 'Unknown'
            emailSubject = "[FAILURE] Pipeline Failed for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The pipeline has FAILED because the ArgoCD application '${env.argoAppName}' in the UAT environment is not healthy.

Current Status: ${healthStatus}

Please check the ArgoCD dashboard and application logs for more details.
"""
            break
            
        case 'PROD_UNHEALTHY':
            def unhealthyDepts = config.EXTRA_DATA?.unhealthyList ?: 'N/A'
            def healthyDepts = config.EXTRA_DATA?.healthyList ?: 'N/A'
            emailSubject = "[FAILURE] Production Deployment Failed for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The production deployment has FAILED because one or more ArgoCD applications did not become healthy after syncing.

Unhealthy Departments: ${unhealthyDepts}
Healthy Departments: ${healthyDepts}

Please check the ArgoCD dashboard for immediate investigation.
"""
            break

        case 'POST_BUILD_REPORT':
            def buildResult = config.EXTRA_DATA?.buildResult ?: 'UNKNOWN'
            emailSubject = "[REPORT] Pipeline ${buildResult} - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            emailBody = """
The CI/CD pipeline has completed with the result: ${buildResult}.

Please review the attached build security report (if any) for further details.
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






// def mail(Map config = [:]) {
//     if (config.MAILMODE == "START-AUTOMATED") {
//         SUBJECT= "[START] Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}"
//         MAILMESSAGE = """
// Your recent commit (${env.commit_sha}) has triggered a new build of ${env.JOB_NAME} #${env.BUILD_NUMBER}.

// Commit Message: 
// ${env.commitMessage}
// """
//     } else if (config.MAILMODE == "START-MANUAL") {
//         SUBJECT= "[START] Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}"
//         MAILMESSAGE = "Your recent manual build of ${env.JOB_NAME} #${env.BUILD_NUMBER} has been started."
//     } else if (config.MAILMODE == "APPROVAL-1") {
//         SUBJECT= "[APPROVAL] Production Deployment - ${env.JOB_NAME}#${env.BUILD_NUMBER}"
//         MAILMESSAGE = """
// """
//     } else if (config.MAILMODE == "APPROVAL-2") {
//         SUBJECT= "[APPROVAL] Production Deployment - ${env.JOB_NAME}#${env.BUILD_NUMBER}"
//         MAILMESSAGE = """
// """
//     }


//     emailext(
//         attachLog: true,
//         subject: SUBJECT,
//         body: """ 
// Hello ${env.authorEmail}, 

// ${MAILMESSAGE}

// You can follow the progress here:
// ${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

// Thank you.

// Best regards,  
// DevOps Team
// """,
//         attachmentsPattern: config.ATTACHMENT,
//         to: """\
//             ${config.RECIPIENTEMAIL},
//             cc:${env.developersEmail},
//             bcc:${env.bccEmail1},
//             bcc:${env.bccEmail2}
//         """
//     )
// }

// // other templates
// def argocdUATUnhealthy(Map config = [:]) {
//     emailext(
//         attachLog: true,
//         subject: "${config.JENKINS_PIPELINE_STATUS} Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
//         body: """ 
// Hello Team,

// The CI/CD pipeline for job ${env.JOB_NAME} with build number #${env.BUILD_NUMBER} has FAILED because the ArgoCD application '${env.argoAppName}' is not healthy (status: ${healthStatus}) or timed out during sync/wait.

// Please check the ArgoCD dashboard and application logs for more details, and check the pipeline logs via the Blue Ocean link below: 
// ${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

// Thank you.
 
// Best regards,  
// DevSecOps Team
// """,
//         attachmentsPattern: 'report.html',
//         to: """\
//             ${env.authorEmail},
//             cc:${env.developersEmail},
//             bcc:${env.bccEmail1},
//             bcc:${env.bccEmail2}
//         """
//     )
// }

// def firstApproval(Map config = [:]) {
//     emailext(
//         attachLog: true,
//         subject: "Production Deployment - Approver 1 Notification - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
//         body: """
// Dear ${env.firstApproverID},

// A deployment to the Production environment has been initiated for the following pipeline:

// Job Name   : ${env.JOB_NAME}  
// Build No.  : ${env.BUILD_NUMBER}

// The pipeline is waiting for your approval to proceed with the deployment.

// Please access the Jenkins pipeline via the following Blue Ocean link to proceed with approval:  
// ${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

// Your prompt attention and decision is highly appreciated to proceed with the production release.

// Best regards,  
// DevSecOps Team
// """,
//         attachmentsPattern: 'report.html',
//         to: """\
//             ${env.firstApproverEmail},
//             bcc:${env.bccEmail1},
//             bcc:${env.bccEmail2}
//         """
//     )
// }

// def secondAprroval(Map config = [:]) {
//     emailext(
//         attachLog: true,
//         subject: "Production Deployment - Approver 2 Notification - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
//         body: """
// Dear ${env.secondApproverID},

// The first approval for deployment to Production environment has been completed by ${firstApprover}.

// Your approval is now needed to proceed with the deployment.

// Please access the Jenkins pipeline via the following Blue Ocean link to provide your approval and please select the departments to deploy:  
// ${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

// Your prompt attention and decision is highly appreciated to proceed with the production release.

// Best regards,  
// DevSecOps Team
// """,
//         attachmentsPattern: 'report.html',
//         to: """\
//                             ${env.secondApproverEmail},
//                             bcc:${env.bccEmail1},
//                             bcc:${env.bccEmail2}
//                         """
//     )
// }

// def argocdProductionUnhealthy(Map config = [:]) {
//     emailext(
//         attachLog: true,
//         subject: "Jenkins Pipeline FAILED - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
//         body: """
// Hello Team,

// The CI/CD pipeline for job ${env.JOB_NAME} with build number #${env.BUILD_NUMBER} has FAILED because the following ArgoCD production applications are not healthy or timed out during sync/wait:

// Unhealthy Departments: ${unhealthyList}

// Healthy Departments: ${healthyList}

// Please check the ArgoCD dashboard and application logs for more details, and check the pipeline logs via the Blue Ocean link below:
// ${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

// Thank you.
 
// Best regards,  
// DevSecOps Team
// """,
//         attachmentsPattern: 'report.html',
//         to: """\
//                                 ${env.authorEmail},
//                                 cc:${env.developersEmail},
//                                 bcc:${env.bccEmail1},
//                                 bcc:${env.bccEmail2}
//                             """
//     )
// }

// def postPipelineEmail(Map config = [:]) {
//     emailext(
//         attachLog: true,
//         subject: "Jenkins Report - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
//         body: """
// Hello Team,

// The CI/CD pipeline for job ${env.JOB_NAME} with build number #${env.BUILD_NUMBER} has completed with the result: ${currentBuild.result}.

// Please review the attached build security report (report.html) for further details.

// Please also review the pipeline details and logs via the Blue Ocean link below:  
// ${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

// Thank you.
 
// Best regards,  
// DevSecOps Team
// """,
//         attachmentsPattern: 'report.html',
//         to: """\
//                     ${env.authorEmail},
//                     cc:${env.developersEmail},
//                     bcc:${env.bccEmail1},
//                     bcc:${env.bccEmail2}
//                 """
//     )
// }