#!/usr/bin/env bash

# Including fuseki-funcions.sh implementing delete, create, and upload.
source fuseki-functions.sh

# Name of the repository
REPOSITORY="knora-test"

# delete-repository // delete dos not work correctly. need to delete database manually.
create-repository
upload-graph ../../knora-ontologies/knora-admin.ttl http://www.knora.org/ontology/knora-admin
upload-graph ../../knora-ontologies/knora-base.ttl http://www.knora.org/ontology/knora-base
upload-graph ../../knora-ontologies/standoff-onto.ttl http://www.knora.org/ontology/standoff
upload-graph ../../knora-ontologies/standoff-data.ttl http://www.knora.org/data/standoff
upload-graph ../../knora-ontologies/salsah-gui.ttl http://www.knora.org/ontology/salsah-gui
upload-graph ../_test_data/all_data/admin-data.ttl http://www.knora.org/data/admin
upload-graph ../_test_data/all_data/permissions-data.ttl http://www.knora.org/data/permissions
upload-graph ../_test_data/all_data/system-data.ttl http://www.knora.org/data/0000/SystemProject
upload-graph ../_test_data/ontologies/incunabula-onto.ttl http://www.knora.org/ontology/0803/incunabula
upload-graph ../_test_data/all_data/incunabula-data.ttl http://www.knora.org/data/0803/incunabula
upload-graph ../_test_data/ontologies/dokubib-onto.ttl http://www.knora.org/ontology/0804/dokubib
upload-graph ../_test_data/ontologies/images-onto.ttl http://www.knora.org/ontology/00FF/images
upload-graph ../_test_data/demo_data/images-demo-data.ttl http://www.knora.org/data/00FF/images
upload-graph ../_test_data/ontologies/anything-onto.ttl http://www.knora.org/ontology/0001/anything
upload-graph ../_test_data/all_data/anything-data.ttl http://www.knora.org/data/0001/anything
upload-graph ../_test_data/ontologies/something-onto.ttl http://www.knora.org/ontology/0001/something
upload-graph ../_test_data/ontologies/beol-onto.ttl http://www.knora.org/ontology/0801/beol
upload-graph ../_test_data/ontologies/biblio-onto.ttl http://www.knora.org/ontology/0801/biblio
upload-graph ../_test_data/ontologies/newton-onto.ttl http://www.knora.org/ontology/0801/newton
upload-graph ../_test_data/ontologies/leibniz-onto.ttl http://www.knora.org/ontology/0801/leibniz
upload-graph ../_test_data/all_data/biblio-data.ttl http://www.knora.org/data/0801/biblio
upload-graph ../_test_data/all_data/beol-data.ttl http://www.knora.org/data/0801/beol
upload-graph ../_test_data/ontologies/webern-onto.ttl http://www.knora.org/ontology/08AE/webern
upload-graph ../_test_data/all_data/webern-data.ttl http://www.knora.org/data/08AE/webern
