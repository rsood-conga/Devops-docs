#--------------------------------------------------------------------------------------------------------------#
# Summary: This Makefile does following things:
# - build: Build the Docker Image
# - push: Push the image in to Artifactory Docker Registry
# - clean: Remove the image and dist folder from local
#--------------------------------------------------------------------------------------------------------------#

SHELL := /bin/bash

INFO=$(shell echo `date +%Y-%m-%d-%H:%M:%S` [INFO])
ERROR=$(shell echo `date +%Y-%m-%d-%H:%M:%S` [ERROR])
WARN=$(shell echo `date +%Y-%m-%d-%H:%M:%S` [WARN])

deploy-all_src:
	@echo "$(INFO) Deploying all src components"
	@source make_script.sh; deploy_all_src $(GIT_ACTION) $(SRC_DIR)	
	@echo "$(INFO) deploy-all src code."

ant_deploy:
	@echo "$(INFO) ant deploy"
	@source make_script.sh; ant_deploy $(GIT_ACTION) $(SRC_DIR)	
	@echo "$(INFO) deploy-all."

ant_deploy_with_test:
	@echo "$(INFO) ant deploy with unit test"
	@source make_script.sh; ant_deploy_with_test $(GIT_ACTION) $(SRC_DIR)	
	@echo "$(INFO) deploy-all."

update-build-properties:
	@echo "$(INFO) update Build.Properties files of salesforce"
	@source make_script.sh; update_buildProperties $(SFDC_USERNAME)  $(SFDC_PASSWORD)  $(SFDC_SERVERURL) $(SFDC_MAXPOLL)  $(SFDC_pollWaitMillis)  $(SFDC_retrieveTarget) $(SRC_DIR)	
	@echo "$(INFO) update Build.Properties."

update-packagexml:
	@echo "$(INFO) update API Version in Package.xml"
	@source make_script.sh; update_packageXMLApiVersion $(API_VERSION) $(SRC_DIR)
	@echo "$(INFO) updated Package.xml."

deploy-changes-only:
	@echo "$(INFO) Deploying only delta changes"
	@source make_script.sh; deploy_changed_files $(GIT_ACTION) $(SRC_DIR) $(COMMIT_TYPE)	
	@echo "$(INFO) deploy-all."    

#deploy-changes-all:
#	@echo "$(INFO) Deploying all delta changes"
#	@source make_script.sh; deploy_all_files $(GIT_ACTION) $(SRC_DIR) $(COMMIT_TYPE) 
#	@echo "$(INFO) deploy-all."

#deploy-timebased:
#	@echo "$(INFO) Deploying timebased changes"
#	@source make_script.sh; deploy_timebased $(GIT_ACTION) $(SRC_DIR) $(TIME_FORMAT) 
#	@echo "$(INFO) deploy-all."

.PHONY: deploy-all_src ant_deploy ant_deploy_with_test update-build-properties update-packagexml deploy-all deploy-changes-only

