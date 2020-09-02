# ![BayEOS Server](docs/server_logo.png)
Server for environmental observation data

Main characteristics 
- Implementation of an observation schema to store observation time series
- Batch aggregation of measurement values based on event times
- Aggregation with delayed out of order handling
- Web service interface 

## Getting Started
### Prerequisites
- Debian 9|10 Linux Server 
- Root login

### Installation
1. Debian Repository
- Login as root
- Install basic tools for installation  
`apt-get update`  
`apt-get install wget gnupg`
- Import the repository key  
`wget -O - http://www.bayceer.uni-bayreuth.de/repos/apt/conf/bayceer_repo.gpg.key | apt-key add -`
- Add this repository on Debian 9/Stretch  
`echo "deb http://www.bayceer.uni-bayreuth.de/repos/apt/debian stretch main" | tee /etc/apt/sources.list.d/bayceer-stretch.list`  
- Add this repository on Debian 10/Buster  
`echo "deb http://www.bayceer.uni-bayreuth.de/repos/apt/debian buster main" | tee /etc/apt/sources.list.d/bayceer-buster.list`  
- Update your repository cache  
`apt-get update`
2. Server   
`apt-get install bayeos-server`
3. Viewer 
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
- [XML-RPC](http://bayceer.github.io/bayeos-xmlrpc/apidocs/)

## History

### Version 2.0.8, 31 Aug, 2020
- XML-RPC Version 2.0.2
- Jetty Update to 9.4.31
- JRE 8|9|10|11 support
- Postgresql 11 support  

### Version 2.0.7, 07 Jan, 2020
- XML-RPC Version 2.0.1

### Version 2.0.6, Dec, 2019
- Jetty Update to 9.4.24
- Bind port to localhost  

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







