var subSocket = null;

var mPendingUnits = [];

var mLoadModal = {
    pendingUnit: {},
    cert: null,
    certs: [
        { name: "a.cert" },
        { name: "b.cert" }
    ],
    passphrase: null,
    decrypting: false,
    nextEnabled: true,
    destinations: [
        "Holding",
        "Pre-Ingest",
        "Holding + Sandbox"
    ]
};

//Controller for displaying Pending Units grid
function PendingUnitsCtrl($scope) {
    $scope.pendingUnits = mPendingUnits;

    $scope.loadDialog = function(pendingUnit) {
        //update the model
        mLoadModal.pendingUnit = pendingUnit;

        //show the load modal
        $('#loadModal').modal('show');
    };
}

//Controller for the Load modal dialog
function LoadModalCtrl($scope, $http) {
    $scope.loadModal = mLoadModal;

   updateCertsModel($http);

    $scope.uploadCertDialog = function() {
        //show the uploadCert modal
        $('#uploadCertModal').modal('show');
    };

    $scope.next = function() {
      var selected = $('#LoadWizard').wizard('selectedItem').step;

      if(selected == 1) {
        //changed "Decrypt" -> "Review Unit"

        //instruct the server to decrypt the unit
        decrypt(mLoadModal.pendingUnit, mLoadModal.cert, mLoadModal.passphrase);

        //show waiting for decrypt
        mLoadModal.decrypting = true;
        mLoadModal.nextEnabled = false;
      }

      if(selected == 2) {
        mLoadModal.nextEnabled = false;
      }

      //$('#LoadWizard').wizard('selectItem', { step: selectedIdx + 1 });
      $('#LoadWizard').wizard('next');
    };

    $scope.startLoad = function () {

        //disable the load button on the main dialog for the unit
        //TODO

        //send message to initiate load
        doStartLoad(mLoadModal.pendingUnit, mLoadModal.cert, mLoadModal.passphrase);

        //hide the load button for this unit and show the progress bar for the load
        updatePending(function(model) {
            $.each(model, function(i, v) {
                if(v.src == mLoadModal.pendingUnit.src) {
                    v.showComplete = true;
                }
            });
        });

        //close modal dialog
        $('#loadModal').modal('hide');

        //reset dialog
        mLoadModal.nextEnabled = true; //re-enable for next time
    };
    
    // Taken from https://coderwall.com/p/ngisma - but disabled as seems to cause other problems!
    /*$scope.safeApply = function(fn) {
        var phase = this.$root.$$phase;
        if(phase == '$apply' || phase == '$digest') {
            if(fn && (typeof(fn) === 'function')) {
                fn();
            }
        } else {
            this.$apply(fn);
        }
    }; */
}

function expandPendingUnitParts(pendingUnit) {
  var parts = [];
  var unitName = pendingUnit.label;
  $.each(pendingUnit.parts, function(i, v) {
    parts.push({
      unit: unitName,
      series: v,
      destination: 'Holding' //TODO get from drop-down(s)
    });
  });
  return parts;
}

function doStartLoad(pendingUnit, cert, pass) {

  var partsCpy = pendingUnit.parts.slice(0);
  $.each(partsCpy, function(i, v) {
    delete v.$$hashKey;
  });

  subSocket.push(JSON.stringify({
    action: 'load',
    isHacky: true, //needed to disambiguate between decrypt and load messages on the server side
    unit: {
        uid: pendingUnit.uid,
        parts: partsCpy
    },
    certificate: cert.name,
    passphrase: pass
  }));
};

function decrypt(pendingUnit, cert, pass) {
  subSocket.push(JSON.stringify({
    action: 'decrypt',
    unit: {
        uid: pendingUnit.uid
    },
    certificate: cert.name,
    passphrase: pass
  }));
};

function updateCertsModel(http) {
    http({method: "GET", url: "/certificate"})
        .success(function(data, status, headers, config) {
            mLoadModal.certs.length = 0;
            $.each(data.certificate, function(i, v) {
                mLoadModal.certs.push(v);
            });
        })
        .error(function(data, status, headers, config) {
           alert("error $http = " + data);
        });
}

//Controller for the UploadCert modal dialog
function UploadCertModalCtrl($scope, $http) {
    $scope.selectedFiles = [];
    $scope.progress = 0;
    $scope.showProgress = false;

    //updates the available certs in the model (for the LoadModalCtrl)
    $scope.reloadCerts = function() {
      updateCertsModel($http);
    };

    $scope.setFiles = function(fileInputElem) {
        $scope.$apply(function($scope) {
            // Turn the FileList object into an Array in the model
            $scope.selectedFiles.length = 0;
            $.each(fileInputElem.files, function(i, v) {
                $scope.selectedFiles.push(v);
            });
            $scope.progress = 0;
        })
    };

    $scope.displaySize = function(size) {
        return toHumanSize(size);
    };

    $scope.upload = function() {

        var fd = new FormData();
        for(var i in $scope.selectedFiles) {
            fd.append("certificate", $scope.selectedFiles[i]);
        }
        var xhr = new XMLHttpRequest();

        xhr.upload.addEventListener("progress", function(evt){
            $scope.$apply(function(){
                if(!$scope.showProgress) {
                    $scope.showProgress = true;
                }
                if(evt.lengthComputable) {
                    $scope.progress = Math.round(evt.loaded * 100 / evt.total);
                } else {
                    console.log("Error: unable to compute length");
                }
            });
        }, false);

        xhr.addEventListener("load", function(evt){
            alert(evt.target.responseText)
        }, false);

        xhr.addEventListener("error",function(evt){
           alert("There was an error attempting to upload the file.")
        }, false);

        xhr.addEventListener("abort", function(evt){
            alert("The upload has been canceled by the user or the browser dropped the connection.")
        }, false);


        xhr.open("POST", "/certificate");
        xhr.send(fd);

        /* WebSocket version below is not working with
        scalatra-atmosphere as it receives the binarymessage
        as a text message. scalatra-atmosphere needs to be fixed
        so that it sees the message as binary
        */

        /*var socket = $.atmosphere;
        var subSocket;
        var transport = 'websocket';

        var request = {
            url: "/certificate/" + $scope.selectedFiles[0].name,
            method: "POST",
            webSocketBinaryType: "arraybuffer",
            contentType: "application/octet-stream",
            logLevel: "debug",
            transport: transport,
            fallbackTransport: "long-polling"
        };

        request.onOpen = function(response) {
            console.log('Atmosphere connected using ' + response.transport);

            transport = response.transport;

            if (response.transport == "local") {
              subSocket.pushLocal("Name?");
            }

            //send file data
            var reader = new FileReader();
            reader.onload = function(evt) {
                request.data = evt.target.result;
                subSocket.push(request);
                //subSocket.push(evt.target.result);
            };

            //TODO send all files
            reader.readAsArrayBuffer($scope.selectedFiles[0]);
        };

        subSocket = socket.subscribe(request);*/
    };
}

function updateLoadModal(fnUpdateModel) {
    updateModel("#loadModal", fnUpdateModel, mLoadModal)
}

function updatePending(fnUpdateModel) {
    updateModel("#pendingUnits", fnUpdateModel, mPendingUnits)
}

function updateModel(ctrlElemId, fnUpdateModel, m) {
    var ctrlElem = $(ctrlElemId);
    var scope = angular.element(ctrlElem).scope();

      //scope.$safeApply(function() {
      scope.$apply(function() {
          fnUpdateModel(m);
      });
}

function toHumanSize(bytes) {
  var KB = 1024;
  var MB = Math.pow(KB, 2);
  var GB = Math.pow(KB, 3);
  var TB = Math.pow(KB, 4);

  if(bytes / TB > 1) {
     return Math.ceil(bytes / TB) + " TB";
  } else if(bytes / GB > 1) {
     return Math.ceil(bytes / GB) + " GB";
  } else if(bytes / MB > 1) {
    return Math.ceil(bytes / MB) + " MB";
  } else if(bytes / KB > 1) {
    return Math.ceil(bytes / KB) + " KB";
  } else {
    return bytes + " bytes";
  }
}

function toHumanTime(timestamp) {
   var d = new Date(timestamp);
   //return d.format("D, dS M Y @ H:i e");
   return d.format("D, dS M Y @ H:i") + " " + d.toString().replace(/[^\(]+(\(.+\))/, "$1"); //workaround to add timezone as 'e' in above code seems broken in date.js library
}

function copyUnitProperties(srcUnit, destUnit) {
   destUnit.label = srcUnit.label;
   destUnit.size = srcUnit.size;
   destUnit.timestamp = srcUnit.size;
   if (srcUnit.parts) {
     destUnit.parts = expandPendingUnitParts(srcUnit);
   }
   //TODO update action and progress of action!
}

$(document).ready(function() {
  "use strict";

  var socket = $.atmosphere;
  var transport = 'websocket';

  var request = {
    url: "/unit",
    contentType: "application/json",
    logLevel: 'debug',
    transport: transport,
    fallbackTransport: 'long-polling'
  };

  request.onOpen = function(response) {
    console.log('Atmosphere connected using ' + response.transport);

    transport = response.transport;

    if (response.transport == "local") {
      subSocket.pushLocal("Name?");
    }

    //request initial unit status
    subSocket.push(JSON.stringify({action: 'pending'}));
  };

  request.onReconnect = function(request, response) {
    socket.info("Reconnecting");
    console.log("Reconnecting...");
  };

  request.onMessage = function(response) {
    var message = response.responseBody;
      try {
          var json = JSON.parse(message);

          //is this an add/update of unit status?
          if(json.update) {
             updatePending(function(model) {
                $.each(json.update.unit, function(i, v) {

                     //format for display
                     v.size = toHumanSize(v.size);
                     v.timestamp = toHumanTime(v.timestamp);

                     //does the model already contain details of this unit?
                     var existingIdx = -1;
                     for(var i = 0; i < model.length; i++) {
                        if(model[i].uid == v.uid) {
                            existingIdx = i;
                            break;
                        }
                     }

                     if(existingIdx > -1) {
                        //do update
                        copyUnitProperties(v, model[existingIdx]);
                     } else {
                        //create new
                        model.push(v);
                     }
                 });
             });

             //update the unit detail part of the load modal dialog
             $.each(json.update.unit, function(i, v) {
                 updateLoadModal(function(model) {
                     if(v.uid == model.pendingUnit.uid) {
                         model.pendingUnit = v;
                         //has part discovery completed? if so disable decrypting message
                         if(v.parts) {
                             model.pendingUnit.parts = expandPendingUnitParts(v);
                             model.decrypting = false;    //hide decrypting message
                             model.nextEnabled = true;
                         }
                     }
                 });
             });


          //is this a removal from the pending units?
          } else if(json.remove) {
           updatePending(function(model) {
              $.each(model, function(i, v) {
               $.each(json.remove.unit, function(y, vv) {
                  if((!(v === undefined)) && v.uid == vv.uid) {
                      model.splice(i, 1);
                  }
               });
              });
           });
          }

          //is this a pending unit initial status
//          if(json.pending) {
//            updatePending(function(model) {
//                model.length = 0; //empty the model
//                $.each(json.pending.unit, function(i, v) {
//                    v.size = toHumanSize(v.size);
//                    v.timestamp = toHumanTime(v.timestamp);
//                    model.push(v); //add to the model
//                });
//            });
//
//          //is this an addition to the pending units
//          } else if(json.pendingAdd) {
//            updatePending(function(model) {
//                $.each(json.pendingAdd.unit, function(i, v) {
//                    v.size = toHumanSize(v.size);
//                    v.timestamp = toHumanTime(v.timestamp);
//                    model.push(v); //add to the model
//                });
//            });
//
//            //update the unit detail part of the load modal dialog
//            $.each(json.pendingAdd.unit, function(i, v) {
//                updateLoadModal(function(model) {
//                    if(v.src == model.pendingUnit.src) {
//                        model.pendingUnit = v;
//                        //has part discovery completed? if so disable decrypting message
//                        if(v.part) {
//                            model.decrypting = false;    //hide decrypting message
//                        }
//                    }
//                });
//            });
//
//          //is this a removal from the pending units
//          } else if(json.pendingRemove) {
//            updatePending(function(model) {
//               $.each(model, function(i, v) {
//                $.each(json.pendingRemove.unit, function(y, vv) {
//                   if((!(v === undefined)) && v.src == vv.src) {
//                       model.splice(i, 1);
//                   }
//                });
//               });
//            });
//          }
//
//          //is this a UnitLoadStatus update?
//          else if(json.loadStatus) {
//            updatePending(function(model) {
//              $.each(model, function(i, v) {
//                if(v.src == json.loadStatus.unit.src) {
//                    v.complete = json.loadStatus.complete;
//                }
//              });
//            });
//          }

      } catch (e) {
          console.log('Error: ', message.data);
          return;
      }

    console.debug("Received message: " + json.toString());
  };

  request.onClose = function(response) {
    console.log("Connection closed! " + response.responseBody)
  };

  request.onError = function(response) {
    console.log("Error: " + response.responseBody);
  };

  subSocket = socket.subscribe(request);

});