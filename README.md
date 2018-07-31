# ![BayEOS Server](docs/gateway_logo_medium.png)
Server for environmental observation data

Main characteristics 
- Implementation of an observation schema to store observation time series
- Batch aggregation of measurement values based on event times
- Aggregation with delayed out of order handling
- Web service interface 

## Getting Started

### Prerequisites
- Debian/Ubuntu Linux Server with a minimum of 2GB RAM is recommended
- Raspian on a Raspberry Pi (Model >=3) is working

### Installing
- Import the repository key  
`wget -O - http://www.bayceer.uni-bayreuth.de/repos/apt/conf/bayceer_repo.gpg.key |apt-key add -`
- Add the following repository to /etc/apt/sources.list  
`deb http://www.bayceer.uni-bayreuth.de/repos/apt/debian stretch main`
- Update your repository cache  
`apt-get update`
- Install the core package   
`apt-get install bayeos-gateway`
- Install the [viewer package](https://github.com/BayCEER/bayeos-viewer-php)  
`apt-get install bayeos-viewer-php`

### Configuration
- Open the URL `http://localhost/bayeosViewer` and log in as user 'root' with password 'bayeos'
- Open the administration menu "Admin/Change Password" to change the default root password

### Clients
- [BayEOS Gateway](https://github.com/BayCEER/bayeos-gateway)
- [BayEOS R Package](https://github.com/BayCEER/BayEOS-R) 
- [BayEOS Python Package](https://github.com/BayCEER/bayeos-python-cli)
- [BayEOS Viewer Package](https://github.com/BayCEER/bayeos-viewer-php)

### API
- XML-RPC
- SQL

## History
### Version 2.0.5, July, 2018
- [BayEOS-XMLRpc library updated to 2.0.0](https://github.com/BayCEER/bayeos-xmlrpc)

### Version 2.0.4, March, 2018
- Embedded Jetty web server 
- Refined import handler interfaces 
- JRE 8 headless 

### Version 1.9, March, 2014
- DataFrame modell 
- Simplyfied installation
- JNDI Tomcat connection pool
- JRE 7 headless
- User bayeos with MD5 authentification

### Version 1.8, December, 2012
- IP authentification filter
- New API funcion to upsert or insert rows
- Search path expressions on all object trees    

### Version 1.7, January, 2011
- LDAP authentification
- UTF-8 encoding
- Aggregation with time zone information
- New function to import observations as binary data 

### Version 1.6, Januar, 2008
- Series state (active, inactive)
- Automatic history archive

### Version 1.5, Januar, 2004
- Batch aggregation of measurement values based on event times for all series data
- Aggregation with delayed out of order handling

### Version 1.4, August, 2003
- First PostgreSQL version
- Crosstable function for matrix exports 
- XML-RPC interface

## License
GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1, February 1999







