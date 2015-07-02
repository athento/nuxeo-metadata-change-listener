# nuxeo-metadata-change-listener-plugin

## Synopsis

This Nuxeo plugin implements a listener for metadata updates on every document.
When a user edits a document, the changed made are appended as a comment in document's history 

## Installation

You just have to compile the pom.xml using Maven and deploy the plugin in 
```{r, engine='bash', count_lines}
cd nuxeo-metadata-change-listener-plugin
mvn clean install
cp target/metadataChangeValueListener-*.jar $NUXEO_HOME/nxserver/plugins
```
And then, restart your nuxeo server and enjoy.

## Versions
1.0 Initial version
1.1 Uses current logged user Locale for metadata value changed messages
1.2 Adds new properties file to control which document types and metadata should be traced. Also adds some properties to translate Dictionary valued metadata.
