#!/usr/bin/env python3

import requests, json, traceback

try:

    # create XSL transformation

    # create mapping referring to the XSL transformation

    mappingParams = {
        "http://api.knora.org/ontology/knora-api/v2#mappingHasName": "BEOLTEIMapping",
        "http://api.knora.org/ontology/knora-api/v2#attachedToProject": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF",
        "http://www.w3.org/2000/01/rdf-schema#label": "TEI mapping"
    }

    mappingRequest = requests.post("http://localhost:3333/v2/mapping",
                                   data={"json": json.dumps(mappingParams)},
                                   files={"xml": ("BEOLTEImapping.xml", open("../../../src/main/resources/BEOLTEImapping.xml"))},
                                   auth=("t.schweizer@unibas.ch", "test"),
                                   proxies={'http': 'http://localhost:3333'})

    print(mappingRequest.text)

    mappingRequest.raise_for_status()



except:
    traceback.print_exc()