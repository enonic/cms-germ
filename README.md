#germ-plugin

#NB Important note about github.com repos with GERM.
There has been some changes regarding security on github.com regarding fetching code with username and password over https. Therefore the Germ jgit client code cannot check out code from github anymore. The reccommended approach is to use another git repository provider, I can reccommend bitbucket, which as free private repositories up to a certain number of collaborators.

G.E.R.M - Git Enonic Release Management - A plugin that connects Enonic CMS to Git, and allows to pull code directly from any
git remote into CMS_HOME resources and plugin folders. Requires that git is installed on the serer.

urlPatterns : /admin/site/[0-9]/germ.*

Status: BETA.

Ready for testing, all core functionality in place: add remote, fetch, reset, status.

Tested on Enonic CMS 4.7.x

##Build

mvn install

##Install

Create germ.properties file in plugins folder and add these properties:
    
    #Key of admin group with germ access from admin (inspect html to get key)
    allowedAdminGroupKey=F6D7DBBA4A714DD2DBC207C0E98B555D39214733
    #These should point to folder where plugins / resources is checked out
    folderWithPlugins=${cms.home}/plugins
    folderWithResources=${cms.home}/germresources

##Germ process

More on GERM here:
https://enonic.com/en/docs/enonic-cms-47?page=Development+Process+-+GERM

##License

This software is licensed under AGPL 3.0 license. Also the distribution includes 3rd party software components.
The vast majority of these libraries are licensed under Apache 2.0. For a complete list please read NOTICE.txt.

You are free to use, change and re-distribute the software according to license
The software is provided "AS IS" - Enonic accepts no liability, indemnity or warranties

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU Affero General Public License as
	published by the Free Software Foundation, either version 3 of the
	License, or (at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU Affero General Public License for more details.
