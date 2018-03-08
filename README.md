# ![BayEOS Server](docs/gateway_logo_medium.png)
Server for environmental observation data

Main characteristics 
- Realization of an observation schema to store observations time series
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
`apt-get install bayeos-gateway bayeos`
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
