def call(body) {
    def pipelineParams= [:]
    def serviceVersionMap = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

	pipeline {
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        agent {
            kubernetes {
                label 'sfdc-builder'
                defaultContainer 'jnlp'
                yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    name: sfdc-builder
spec:
  containers:
  - name: ic-tag-builder
    image: art01-ic-devops.jfrog.io/ic-tag-builder:1.x
    command:
    - cat
    tty: true
  - name: ic-ant-builder
    image: art01-ic-devops.jfrog.io/ic-ant-builder:1.10.1.46
    command:
    - cat
    tty: true
    volumeMounts:
    - mountPath: /var/run/docker.sock
      name: host-file
  - name: ic-utility-builder
    image: art01-ic-devops.jfrog.io/ic-utility-builder:1.1.1
    command:
    - cat
    tty: true
    volumeMounts:
    - mountPath: /var/run/docker.sock
      name: host-file 
  volumes:
  - name: host-file
    hostPath:
      path: /var/run/docker.sock
      type: File
  imagePullSecrets:
  - name: devops-docker-secret
  """
            }
        }
        parameters {
            booleanParam(
                name: 'DEPLOY_ALL', 
                defaultValue: false, 
                description: 'Enable this for all SRC deployment'
            )
            booleanParam(
                name: 'DEPLOY_WITH_LAST_COMMIT', 
                defaultValue: true, 
                description: 'Enable this to deploy changes from the Last commit'
            )
            booleanParam(
                name: 'DEPLOY_WITH_LAST_NTH_COMMIT', 
                defaultValue: false, 
                description: 'Enable this to deploy changes from the Last Nth commit'
            )
            string(
                name: 'LAST_NTH_COMMIT', 
                defaultValue: '1', 
                description: 'deploy changes from the Last Nth commit, e.g last 4 commits'
            )            
            booleanParam(
                name: 'DEPLOY_WITH_COMMIT_ID', 
                defaultValue: false, 
                description: 'Enable this to deploy changes from given commitID'
            ) 
            string(
                name: 'COMMIT_ID', 
                defaultValue: '', 
                description: 'add commitid: e.g 6109145abb4845c61eafa8fcccb1b2cf1b77c51'
            )
            /*            
            booleanParam(
                name: 'DEPLOY_WITH_TIME_FRAME', 
                defaultValue: false, 
                description: 'Enable this for all SRC deployment'
            )
            string(
                name: 'TIME_FRAME', 
                defaultValue: '1.days.ago', 
                description: '1.week.ago, 3.days.ago, 12.hours.ago, 1.minutes.ago'
            )*/
            booleanParam(
                name: 'DEPLOY_WITH_DEFAULT_SFDC_CONFIGURATION', 
                defaultValue: true, 
                description: 'Enable this for sfdc credentials from default build.properties file'
            )
            booleanParam(
                name: 'DEPLOY_WITH_UNIT_TEST', 
                defaultValue: false, 
                description: 'Enable this to deploy sfdc component with apex unit test'
            )
            string(
                name: 'SFDC_USERNAME', 
                defaultValue: '', 
                description: 'Salesforce username for the desired Salesforce organization'
            )                           
            string(
                name: 'SFDC_PASSWORD', 
                defaultValue: '', 
                description: 'Salesforce password for the desired Salesforce organization'
            )
            string(
                name: 'SFDC_SERVERURL', 
                defaultValue: '', 
                description: 'Salesforce serverurl for the desired Salesforce organization'
            )  
            string(
                name: 'SFDC_MAXPOLL', 
                defaultValue: '', 
                description: 'The number of polling attempts to be performed before aborting. The default value is 200'
            )
            string(
                name: 'API_VERSION', 
                defaultValue: '45.0', 
                description: 'API version to be use in package.xml manifest file'
            ) 
            string(
                name: 'SFDC_pollWaitMillis', 
                defaultValue: '', 
                description: 'The number of milliseconds to wait between polls for retrieve/deploy results'
            )
            string(
                name: 'SFDC_retrieveTarget', 
                defaultValue: '', 
                description: 'Salesforce retrieveTarget for the desired Salesforce organization'
            )            

        }
        stages {
 
            stage('initialize'){
                steps {
	                script{

                        jenkinsFilePath="${pipelineParams.jenkinsFilePath}"
                        (jenkinsFilePath == "") ? jenkinsFilePath="./":""

                        copyGlobalLibraryScript(jenkinsFilePath,'sfdc/Makefile')
                        copyGlobalLibraryScript(jenkinsFilePath,'sfdc/make_script.sh')

	                    git_branch="${GIT_BRANCH}" 
                        def workspace = pwd()
                        if (params.DEPLOY_ALL) {
                            GIT_ACTION='--deployall'
                            COMMIT_ACTION='all'
                        }
                        if (params.DEPLOY_WITH_LAST_COMMIT) {
                            GIT_ACTION='--last-commit'
                            COMMIT_ACTION='HEAD~1..HEAD'
                        }
                        if (params.DEPLOY_WITH_LAST_NTH_COMMIT) {
                            GIT_ACTION='--last-commit'
                            COMMIT_ACTION="HEAD~${LAST_NTH_COMMIT}..HEAD"
                        }                                                 
                        if (params.DEPLOY_WITH_COMMIT_ID) {
                            GIT_ACTION="--commit-id"
                            COMMIT_ACTION="${COMMIT_ID}"
                        } /*
                        if (params.DEPLOY_WITH_TIME_FRAME) {
                            GIT_ACTION="--time-based"
                            COMMIT_ACTION="${TIME_FRAME}"
                        }*/

                        if (!params.DEPLOY_WITH_DEFAULT_SFDC_CONFIGURATION) {
                            //read from jenkins parameters
                            echo "Reading sfdc parameters from jenkins parameters"
                            SFDC_USERNAME="${SFDC_USERNAME}" 
                            SFDC_PASSWORD="${SFDC_PASSWORD}" 
                            SFDC_SERVERURL="${SFDC_SERVERURL}"
                            SFDC_MAXPOLL="${SFDC_MAXPOLL}"
                            SFDC_pollWaitMillis="${SFDC_pollWaitMillis}"
                            SFDC_retrieveTarget="${SFDC_retrieveTarget}"
                        } 
                        else
                        {
 							// from build.properties
                            echo "using default sfdc build.properties file"
							sfdcPropertiesPath= "build/build.properties"
							sfdcProperties = readProperties file: sfdcPropertiesPath
                            

							// from build.properties
							SFDC_USERNAME=sfdcProperties['sfdc.username']
							SFDC_PASSWORD=sfdcProperties['sfdc.password']
							SFDC_SERVERURL=sfdcProperties['sfdc.serverurl']
							SFDC_MAXPOLL=sfdcProperties['sfdc.maxPoll']
							SFDC_pollWaitMillis=sfdcProperties['sfdc.pollWaitMillis']
							SFDC_retrieveTarget=sfdcProperties['sfdc.retrieveTarget']                           
                        }    

                        // Test condition for mutual exclusion.
                        // Only one deployment method should be use per job
                        if (params.DEPLOY_ALL && (params.DEPLOY_WITH_LAST_COMMIT || params.DEPLOY_WITH_COMMIT_ID || params.DEPLOY_WITH_LAST_NTH_COMMIT)) {
                            error("Please select only one deployment method")
                        }
                        if (params.DEPLOY_WITH_LAST_COMMIT && (params.DEPLOY_ALL || params.DEPLOY_WITH_COMMIT_ID || params.DEPLOY_WITH_LAST_NTH_COMMIT)) {
                            error("Please select only one deployment method")
                        }
                        if (params.DEPLOY_WITH_COMMIT_ID && (params.DEPLOY_ALL || params.DEPLOY_WITH_LAST_COMMIT || params.DEPLOY_WITH_LAST_NTH_COMMIT)) {
                            error("Please select only one deployment method")
                        }
                        if (params.DEPLOY_WITH_LAST_NTH_COMMIT && (params.DEPLOY_ALL || params.DEPLOY_WITH_COMMIT_ID || params.DEPLOY_WITH_LAST_COMMIT)) {
                            error("Please select only one deployment method")   
                        }
                    }                    
	            } //steps
		    } //stage

            stage('update Build.properties files'){
                steps {
	                script{
	                    container('ic-ant-builder'){
                    
                            sh """
                                echo "sfdc_username: ${SFDC_USERNAME}"
                                echo "sfdc_password: ${SFDC_PASSWORD}"
                                echo "sfdc_serverurl: ${SFDC_SERVERURL}"
                                echo "sfdc_maxPoll: ${SFDC_MAXPOLL}"
                                echo "sfdc_pollWaitMillis: ${SFDC_pollWaitMillis}"
                                echo "sfdc_retrieveTarget: ${SFDC_retrieveTarget}"                                
                                make update-build-properties SFDC_USERNAME=${SFDC_USERNAME} SFDC_PASSWORD=${SFDC_PASSWORD} SFDC_SERVERURL=${SFDC_SERVERURL} SFDC_MAXPOLL=${SFDC_MAXPOLL} SFDC_pollWaitMillis=${SFDC_pollWaitMillis} SFDC_retrieveTarget=${SFDC_retrieveTarget} SRC_DIR=${workspace}
                            """
                        }                  
                    }                    
	            } //steps
		    } //stage
            stage('update package.xml'){
                steps {
	                script{
	                    container('ic-ant-builder'){
                    
                            sh """
                                make update-packagexml API_VERSION=${API_VERSION} SRC_DIR=${workspace}            
                            """
                        }                  
                    }                    
	            } //steps
		    }

            stage('deploy changed files'){
                when { expression { return (!params.DEPLOY_ALL) } }
                steps {
	                script{
	                    container('ic-ant-builder'){
                    
                            sh """
                                echo "deploy changed files"
                                echo "GIT_ACTION: ${GIT_ACTION}"
                                echo "SRC_DIR: ${git_branch}"
                                echo "COMMIT_ACTION: ${COMMIT_ACTION}"
                                echo "workspace: $workspace"
                                make deploy-changes-only GIT_ACTION=${GIT_ACTION} SRC_DIR=${workspace} COMMIT_TYPE=${COMMIT_ACTION}
                            """
                        }                  
                    }                    
	            } //steps
		    } //stage

            stage('deploy all src code'){
                when { expression { return (params.DEPLOY_ALL) } }
                steps {
	                script{
	                    container('ic-ant-builder'){
                    
                            sh """
                                echo "deploy all src code"
                                echo "GIT_ACTION: ${GIT_ACTION}"
                                echo "SRC_DIR: ${git_branch}"
                                echo "COMMIT_ACTION: ${COMMIT_ACTION}"
                                echo "workspace: $workspace"
                                make deploy-all_src GIT_ACTION=${GIT_ACTION} SRC_DIR=${workspace} 
                            """
                        }                  
                    }                    
	            } //steps
		    } //stage

            stage('ant deploy '){
                when { expression { return (!params.DEPLOY_WITH_UNIT_TEST) } }
                steps {
	                script{
	                    container('ic-ant-builder'){
                    
                            sh """
                                echo "ant deploy"
                                echo "deploy all src code"
                                echo "SRC_DIR: ${git_branch}"
                                make ant_deploy GIT_ACTION=${GIT_ACTION} SRC_DIR=${workspace} 
                            """
                        }                  
                    }                    
	            } //steps
		    } //stage

            stage('ant deploy with apex unit test '){
                when { expression { return (params.DEPLOY_WITH_UNIT_TEST) } }
                steps {
	                script{
	                    container('ic-ant-builder'){
                    
                            sh """
                                echo "ant deploy"
                                echo "deploy all src code"
                                echo "SRC_DIR: ${git_branch}"
                                make ant_deploy_with_test GIT_ACTION=${GIT_ACTION} SRC_DIR=${workspace} 
                            """
                        }                  
                    }                    
	            } //steps
		    } //stage            
		}// stages
	     
    }// Pipeline
} // call body


/**
  * Generates a path to a temporary file location, ending with {@code path} parameter.
  * 
  * @param path path suffix
  * @return path to file inside a temp directory
  */
@NonCPS
String createTempLocation(String jenkinsFilePath, String path) {
  String tmpDir = pwd tmp: true
  def workspace = env.WORKSPACE + File.separator + jenkinsFilePath
  return workspace + File.separator + new File(path).getName()
}

/**
  * Returns the path to a temp location of a script from the global library (resources/ subdirectory)
  *
  * @param srcPath path within the resources/ subdirectory of this repo
  * @param destPath destination path (optional)
  * @return path to local file
  */
String copyGlobalLibraryScript(String jenkinsFilePath, String srcPath, String destPath = null) {
  destPath = destPath ?: createTempLocation(jenkinsFilePath, srcPath)
  writeFile file: destPath, text: libraryResource(srcPath)
  echo "copyGlobalLibraryScript: copied ${srcPath} to ${destPath}"
  return destPath
}
