window.onload = function(e){

        var JiraLinksSettings,
        JiraLinks = {
        settings: {
            jiraBaseUrl: 'http://issues',
            commitsComment: document.getElementById('commits'),
            jiralinks: document.querySelectorAll('[data-jiralink]'),
            jiraLinkPattern: new RegExp("([A-Z]{1,16}-[0-9]+)"),
            jiraUrl: 'http://issues/browse/'
        },

        init: function(){
            JiraLinksSettings = this.settings;
            this.createJIRALinks();
            this.bindUIActions();
        },

        bindUIActions: function(){
            JiraLinksSettings.commitsComment.addEventListener("click",function(e){
                JiraLinks.resolveJiraLinks(e);
            });
        },
        createJIRALinks: function(){
            [].forEach.call(
                JiraLinksSettings.jiralinks,
                function(el){
                    var potentialJiraLinkText = el.textContent;
                    if (JiraLinksSettings.jiraLinkPattern.test(potentialJiraLinkText)){
                        var jiraIssue = JiraLinksSettings.jiraLinkPattern.exec(potentialJiraLinkText)[0];
                        var pNode = document.createElement("p");
                        var aNode = document.createElement("a");
                        var aNodeText = document.createTextNode(jiraIssue);
                        aNode.appendChild(aNodeText);
                        aNode.title="View jira issue " + jiraIssue;
                        aNode.href=JiraLinksSettings.jiraUrl+jiraIssue;
                        pNode.appendChild(aNode);
                        el.insertBefore(pNode,el.firstChild);
                    }
                }
            )


        },
        resolveJiraLinks: function(e){
            var clickedEl = e.target;
            console.log(clickedEl.textContent);
        }
    }
    JiraLinks.init();
}