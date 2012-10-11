
function scriptler_initDetailLink(rootURL, referenceTag){
   var selId = referenceTag.value;
   var all = new Array();
   all = document.getElementsByName('scriptlerScriptId');
   for(var i = 0; i < all.length; i++) {
	   if(referenceTag == all.item(i)){
		   var detailsLinkTag = document.getElementsByName('showScriptlerDetailLink').item(i);
		   if(selId.length != 0){
			   detailsLinkTag .href=rootURL+"/scriptler/showScript?id=".concat(selId);
			   detailsLinkTag .style.display = 'block';
			}else{
			   detailsLinkTag .style.display = 'none';
			}
	   }
   }
}


function scriptler_descArguments(referenceTag, params){
   var all = new Array();
   all = document.getElementsByName('scriptlerScriptId');
   for(var i = 0; i < all.length; i++) {
	   if(referenceTag == all.item(i)){
		   var desc = "";
		   for(var j = 0; j < params.length; j++) {
			   desc += j+": "+ params[j].name +" ";
		   }
		   var descriptionTag = document.getElementsByName('scriptlerParameters').item(i);
		   descriptionTag.innerHTML = desc;
	   }
    }	   
}

function scriptler_showParams(referenceTag, scriptId){
	scriptlerBuilderDesc.getParameters(scriptId, function(t) {
		var params = t.responseObject();
		if(params != null){
			scriptler_descArguments(referenceTag, params);
		}
    });
}


