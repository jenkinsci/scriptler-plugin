function scriptler_initDetailLink(rootUrl, referenceTag) {
  var itemUrl = referenceTag.dataset.itemUrl;
  var selId = referenceTag.value;
  var all = new Array();
  all = document.getElementsByName("scriptlerScriptId");
  for (var i = 0; i < all.length; i++) {
    if (referenceTag == all.item(i)) {
      var detailsLinkTag = document.getElementsByName("showScriptlerDetailLink").item(i);
      if (selId.length != 0) {
        detailsLinkTag.href = rootUrl + "/" + itemUrl + "scriptler/showScript?id=".concat(selId);
        detailsLinkTag.style.display = "block";
      } else {
        detailsLinkTag.style.display = "none";
      }
    }
  }
}

function scriptler_descArguments(referenceTag, params) {
  var all = new Array();
  all = document.getElementsByName("scriptlerScriptId");
  for (var i = 0; i < all.length; i++) {
    if (referenceTag == all.item(i)) {
      var desc = "";
      for (var j = 0; j < params.length; j++) {
        desc += j + ": " + params[j].name + " ";
      }
      var descriptionTag = document.getElementsByName("scriptlerParameters").item(i);
      descriptionTag.innerText = desc;
    }
  }
}

function scriptler_showParams(referenceTag, scriptId) {
  scriptlerBuilderDesc.getParameters(scriptId, function (t) {
    var params = t.responseObject();
    if (params != null) {
      scriptler_descArguments(referenceTag, params);
    }
  });
}

Behaviour.specify("select[name='scriptlerScriptId']", "ScriptlerBuilderSelect", 0, function (element) {
  const script = document.querySelector("#scriptler-builder-behaviour");
  const rootUrl = script.dataset.rootUrl;
  element.addEventListener("change", function (event) {
    const target = event.target;
    scriptler_initDetailLink(rootUrl, target);
    scriptler_showParams(target, target.value);
  });
});

Behaviour.specify("a[name='showScriptlerDetailLink']", "ScriptlerBuilderDetailLink", 0, function (element) {
  element.addEventListener("click", function (event) {
    event.preventDefault();
    const target = event.target;
    window.open(target.href, "window", "width=900,height=640,resizable,scrollbars,toolbar,menubar");
  });
});

document.addEventListener("DOMContentLoaded", function () {
  const script = document.querySelector("#scriptler-builder-behaviour");
  const rootUrl = script.dataset.rootUrl;
  const hasPermission = script.dataset.hasPermission;

  var all = new Array();
  all = document.getElementsByName("scriptlerScriptId");
  for (var i = 0; i < all.length; i++) {
    all.item(i).disabled = !hasPermission;
    scriptler_initDetailLink(rootUrl, all.item(i));
    scriptler_showParams(all.item(i), all.item(i).value);
  }

  // remember the job name to send it along with the form
  var jobName = document.getElementsByName("name").item(0).value;
  var allBackupJobNames = document.getElementsByName("backupJobName");
  for (var i = 0; i < allBackupJobNames.length; i++) {
    allBackupJobNames.item(i).value = jobName;
  }
});
