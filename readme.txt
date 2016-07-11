README

EgaConfigService.jar  **PROTOTYPE**

EGA Configuration server: internally provides load-balanced URLs for services.

Startup: 

	java -jar EgaConfigService.jar [-l, -f, -p, -t]

"java -jar EgaConfigService.jar" starts the service usinf default configuration (Port 9128, using config file 'DownloadEcosystem.xml' located in directory './../headers/'. 
	Parameter '-l' allows to specify a different path to the config XML file
	Parameter '-f' allows to specify a different name of the config XML file
	Parameter '-p' allows to specify a different port for the service to listen
	Parameter '-t' performs a self-test to the service

Startup as service:

	nohup java -jar EgaConfigService.jar > config.log 2>&1&

The service creates a new directory "dailylog". In this directory there is a log file that lists all requests sent to this service. A new log file is created at the beginning f each new day.

------------

Project is created using Netbeans.

Netbans uses ant, so it can be built using ant (version 1.8+). Ant target "package-for-store" creates the packaged Jar file containing all libraries. There are no further dependencies, everything is packaged in the Jar file.

Servers use Netty as framework. Client REST calls use Resty.

------------

The service currently runs HTTP only. This will be a vault-internal service, so I expect it to remain HTTP.

------------

API
 Base URL: /ega/rest/config/v1

 /ega/rest/config/v1/servers/types                return available types
 /ega/rest/config/v1/servers/{type}               return servers for specified type
 /ega/rest/config/v1/stats/load                   returns server CPU load
 /ega/rest/config/v1/stats/hits[?minutes=minutes] returns # of hits (approximate) within the past {minutes} minutes
 /ega/rest/config/v1/stats/avg[?minutes=minutes]  returns response time/avg of hits (approximate) within the past {minutes} minutes

{type} is a server type specified in the 'DownloadEcosystem.xml' configuration file. The list of available types is returned via the '/type' query.
