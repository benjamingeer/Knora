#!/usr/bin/env python3

import requests, json

#
# This scripts tests the creation of a Thing.
#

try:
    base_url = "http://localhost/v1/"

    params = {
        "_link": [{
            "start": 0,
            "end": 4,
            "resid": "http://data.knora.org/9935159f67",
            "href": "http://data.knora.org/9935159f67"
        },{
            "start": 5,
            "end": 7,
            "href": "http://www.google.ch"
        }],
        "bold": [{
            "start": 9,
            "end": 11
        },
        {
            "start": 16,
            "end": 18
        }]
    }

    xml = """<?xml version="1.0" encoding="UTF-8"?>
       <text>
            <u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="http://data.knora.org/9935159f67">links</a> to a thing
       </text>
    """

    params = {
       "restype_id": "http://www.knora.org/ontology/anything#Thing",
       "label": "A thing to test with",
       "project_id": "http://data.knora.org/projects/anything",
       "properties": {
            "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml": xml, "mapping_id": "http://data.knora.org/projects/standoff/mappings/StandardMapping"}}],
            "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}]
       }
    }

    props = json.dumps(params)

    r = requests.post(base_url + 'resources',
                      data=props,
                      headers={'content-type': 'application/json; charset=utf8'},
                      auth=('root', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    r.raise_for_status()
    print(r.text)
except Exception as e:
    print('Knora API answered with an error:\n')
    print(e)
    print(r.text)
