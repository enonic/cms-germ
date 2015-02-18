window.onload = function(e){

        var IssuetrackerLinksSettings,
        IssuetrackerLinks = {
        settings: {
            commitsComment: document.getElementById('commits'),
            issuetrackerLinks: document.querySelectorAll('[data-issuetrackerlink]'),
            issuetrackerLinkPattern: new RegExp(document.querySelector('body').getAttribute('data-germ-issuetrackerlinkpattern')),
            issuetrackerUrl: document.querySelector('body').getAttribute('data-germ-issuetrackerurl')
        },

        init: function(){
            IssuetrackerLinksSettings = this.settings;
            this.createIssuetrackerLinks();
            this.bindUIActions();
        },

        bindUIActions: function(){
            IssuetrackerLinksSettings.commitsComment.addEventListener("click",function(e){
                IssuetrackerLinks.resolveIssuetrackerLinks(e);
            });
        },
        createIssuetrackerLinks: function(){
            [].forEach.call(
                IssuetrackerLinksSettings.issuetrackerLinks,
                function(el){
                    var potentialIssueLinkText = el.textContent;
                    if (IssuetrackerLinksSettings.issuetrackerLinkPattern.test(potentialIssueLinkText)){
                        var issuetrackerIssue = IssuetrackerLinksSettings.issuetrackerLinkPattern.exec(potentialIssueLinkText)[0];
                        var pNode = document.createElement("p");
                        var aNode = document.createElement("a");
                        var aNodeText = document.createTextNode(issuetrackerIssue);
                        aNode.appendChild(aNodeText);
                        aNode.title="View issues " + issuetrackerIssue;
                        aNode.href=IssuetrackerLinksSettings.issuetrackerUrl+issuetrackerIssue;
                        pNode.appendChild(aNode);
                        el.insertBefore(pNode,el.firstChild);
                    }
                }
            )


        },
        resolveIssuetrackerLinks: function(e){
            var clickedEl = e.target;
        }
    }
    IssuetrackerLinks.init();
}