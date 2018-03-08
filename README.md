#![BayEOS Server](docs/gateway_logo_medium.png)
Server for environmental observation data

##Getting Started

###Prerequisites
- Debian/Ubuntu Linux Server with a minimum of 2GB RAM is recommended
- Raspian on a Raspberry Pi (Model >=3) is working

###Installing
- Import the repository key `wget -O - http://www.bayceer.uni-bayreuth.de/repos/apt/conf/bayceer_repo.gpg.key |apt-key add -`
- Add the following repositories to /etc/apt/sources.list `deb http://www.bayceer.uni-bayreuth.de/repos/apt/debian stretch main`
- Update your repository cache `apt-get update`
- Install the core package 
`apt-get install bayeos-gateway bayeos`
- Install the [viewer package](https://github.com/BayCEER/bayeos-viewer-php) 
`apt-get install bayeos-viewer-php`

