set -eo pipefail 
#------------------------------------------------------------------#
# Function Name: LOG_INFO
# Description: This method is used for printing INFO logs
#------------------------------------------------------------------#
function LOG_INFO()
{
    LOG_MESSAGE_LINE="`date +%Y-%m-%d-%H:%M:%S` [INFO]  $1"
    echo -e  $LOG_MESSAGE_LINE
}

# overide sfdc parameters from jenkins 
function update_buildProperties {
    sfdc_username=${1}
    sfdc_password=${2}
    sfdc_serverurl=${3}
    sfdc_maxPoll=${4}
    sfdc_pollWaitMillis=${5}
    sfdc_retrieveTarget=${6}
    SRC_DIR=${7}
    cd ${SRC_DIR}
    cp ${SRC_DIR}/build/build.properties .
    sed -E -i 's|^(sfdc.username[[:blank:]]*=[[:blank:]]*).*|\1'${sfdc_username}'|' build.properties
    sed -E -i 's|^(sfdc.password[[:blank:]]*=[[:blank:]]*).*|\1'${sfdc_password}'|' build.properties
    sed -E -i 's|^(sfdc.serverurl[[:blank:]]*=[[:blank:]]*).*|\1'${sfdc_serverurl}'|' build.properties
    sed -E -i 's|^(sfdc.maxPoll[[:blank:]]*=[[:blank:]]*).*|\1'${sfdc_maxPoll}'|' build.properties
    sed -E -i 's|^(sfdc.pollWaitMillis[[:blank:]]*=[[:blank:]]*).*|\1'${sfdc_pollWaitMillis}'|' build.properties
    sed -E -i 's|^(sfdc.retrieveTarget[[:blank:]]*=[[:blank:]]*).*|\1'${sfdc_retrieveTarget}'|' build.properties   
    LOG_INFO "build.properties file updated"
    cat build.properties
}

#update package.xml api version from jenkins parameter
function update_packageXMLApiVersion() 
{
    api_version=${1}
    SRC_DIR=${2}
    package_file=${SRC_DIR}/src/package.xml
    if [ -s $package_file ]; then
       LOG_INFO "updating api version to $api_version"
       sed -E -i 's/ xmlns.*=".*"//g' $package_file
       xmlstarlet edit --inplace --update "//Package/version" --value $api_version $package_file
       LOG_INFO "$package_file content are..."
       cat ${SRC_DIR}/src/package.xml
    else
       LOG_INFO "no $package_file found"
       exit 0    
    fi
}

# commented time based deployment
: '
function deploy_timebased()
{
    GIT_ACTION=${1}
    SRC_DIR=${2}
    TIME_FORMAT=${3}
    ANT_PROJECT=${SRC_DIR}/deploy
    LOG_INFO "TIME_FORMAT is: $TIME_FORMAT"
    LOG_INFO "Time based deployment"
    git diff --name-only $(git log -1 --before=@{$TIME_FORMAT} --format=%H) --stat > $SRC_DIR/modified.xml
    if [ -s $SRC_DIR/modified.xml ]; then
        LOG_INFO "File not empty"
        LOG_INFO "download files from time ${TIME_FORMAT} to $ANT_PROJECT folder"
        git archive --format=zip --output=last_commit_files.zip $(git log -1 --before=@{$TIME_FORMAT} --format=%H) `git diff --name-only $(git log -1 --before=@{$TIME_FORMAT} --format=%H) --stat `
        else
           LOG_INFO "File empty"
           exit 0
    fi
    unzip last_commit_files.zip -d $ANT_PROJECT
    copy_to_deploy ${GIT_ACTION} ${SRC_DIR}/modified.xml ${SRC_DIR}
}
'

function deploy_changed_files()
{
    GIT_ACTION=${1}
    SRC_DIR=${2}
    COMMIT_TYPE=${3}
    ANT_PROJECT=${SRC_DIR}/deploy
    LOG_INFO "COMMIT_TYPE is: $COMMIT_TYPE"
    if [ ${GIT_ACTION} != "--last-commit" ]; then
        git diff-tree -r -m --no-commit-id --name-only --diff-filter=ACMRT "$COMMIT_TYPE" >$SRC_DIR/modified.xml
        if [ -s $SRC_DIR/modified.xml ]; then
            LOG_INFO "File not empty"
            LOG_INFO "download files changes from $COMMIT_TYPE to $ANT_PROJECT folder"
            git diff-tree -m --no-commit-id --name-only -r --diff-filter=ACMRT "$COMMIT_TYPE" | awk '{ if ($1 != "D") printf("\"%s\"\n", substr($0,1)) }' |  xargs git archive -v -o diff_changes.zip "$COMMIT_TYPE" --
        else
            LOG_INFO "File empty"
            exit 0
        fi
    else
        LOG_INFO "GIT_ACTION is: $GIT_ACTION"
        git diff-tree -r -m --no-commit-id --name-only --diff-filter=ACMRT $COMMIT_TYPE >$SRC_DIR/modified.xml
        if [ -s $SRC_DIR/modified.xml ]; then
            LOG_INFO "File not empty"
            LOG_INFO "download files changes from $COMMIT_TYPE to $ANT_PROJECT folder"
            git diff-tree -m --no-commit-id --name-only -r --diff-filter=ACMRT "$COMMIT_TYPE" | awk '{ if ($1 != "D") printf("\"%s\"\n", substr($0,1)) }' |  xargs git archive -v -o diff_changes.zip HEAD --
        else
            LOG_INFO "File empty"
            exit 0
        fi
    fi
    unzip diff_changes.zip -d $ANT_PROJECT
    copy_to_deploy ${GIT_ACTION} ${SRC_DIR}/modified.xml ${SRC_DIR}
}

function deploy_all_src()
{ 
    GIT_ACTION=${1}
    SRC_DIR=${2}

    LOG_INFO "deploying all src"
    find . \( -regex '\./src/.*' \) -mindepth 1 | sed 's|^\./||' > ${SRC_DIR}/modified.xml
    copy_to_deploy ${GIT_ACTION} ${SRC_DIR}/modified.xml ${SRC_DIR}

}

# Copy changed artifacts and meta xml files
function copy_to_deploy() {
    GIT_ACTION=${1}
    temp_modifiedfile=${2}
    SRC_DIR=${3}
    ANT_PROJECT=${SRC_DIR}/deploy
    LOG_FILE=${SRC_DIR}/process-logs.txt
    CODE_PKG=src
    cd $SRC_DIR
    mkdir -p $ANT_PROJECT/$CODE_PKG
    LOG_INFO "deploying to $ANT_PROJECT"

    DEPLOY_FILES=false
    while read line; do
	    if [[ $line =~ ^src\/(objects|classes|triggers|pages|components|labels|staticresources|layouts|tabs)\/.*[^xml]$ ]]; then
		   if [ ${GIT_ACTION} == "--deployall" ]; then
			    LOG_INFO $line >> ${LOG_FILE}
			    LOG_INFO $line
		    else
			    LOG_INFO $line >> ${LOG_FILE}
            fi

		DEPLOY_FILES=true
		DIR=$(dirname "${line}")		
		mkdir -p $ANT_PROJECT/${DIR}
            if [ ${GIT_ACTION} == "--deployall" ]; then
		       cp -f "${SRC_DIR}/${line}" "${ANT_PROJECT}/${DIR}"
            fi
	    fi

	if [[ $line =~ ^src\/(classes|pages|components|triggers)\/.*[^xml]$ ]]; then
		cp -f "${SRC_DIR}/${line}-meta.xml" "${ANT_PROJECT}/${line}-meta.xml"
	fi
    done < $temp_modifiedfile
    # remove files 
    rm -rf modified.xml   
    rm -rf last_commit_files.zip 
}

function ant_deploy() {
    LOG_INFO "deploy only"   
    GIT_ACTION=${1}
    SRC_DIR=${2}
    TARGET_DIR=${SRC_DIR}/deploy/src
    ls -lart
    if find "$TARGET_DIR" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
        LOG_INFO "ant deploy begins"
        cp ${SRC_DIR}/src/package.xml $TARGET_DIR
    else
        LOG_INFO "Target '$TARGET_DIR' is empty or not a directory"
        LOG_INFO "nothing to deploy"
        exit 0
    fi

    if [ ${GIT_ACTION} == "--deployall" ]; then
        # excluding files from /deploy/src and copy to dist folder 
		ant copydir
        # deploy all src code to salesforce org 
        ant deployall
    else
        # delta changes deployment only
        ant deploydelta      
    fi 
}

function ant_deploy_with_test() {
    LOG_INFO "deploy with unittest"   
    GIT_ACTION=${1}
    SRC_DIR=${2}
    TARGET_DIR=${SRC_DIR}/deploy/src
    ls -lart

    if find "$TARGET_DIR" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
        LOG_INFO "ant deploy begins"
        cp ${SRC_DIR}/src/package.xml $TARGET_DIR
    else
        LOG_INFO "Target '$TARGET_DIR' is empty or not a directory"
        LOG_INFO "nothing to deploy"
        exit 0
    fi

   if [ ${GIT_ACTION} == "--deployall" ]; then
        #excluding files from /deploy/src and copy to dist folder 
		ant copydir
        # deploy all src code to salesforce org and test
        ant deployallWithTest
    else
        # delta changes deployment and test
        ant deploydeltaWithTest      
    fi 
}
