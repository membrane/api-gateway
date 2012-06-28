var membrane = function() {
				
	function loadExchange (exchangeId) {	
		var exchangeUrl = '/admin/rest/exchanges/'+exchangeId,

			responseBodyUrl = exchangeUrl+'/response/body',
			responseRawUrl = exchangeUrl+'/response/raw',
			responseBodyBeautifiedUrl = '/admin/web/exchanges/'+exchangeId+'/response/body',
			responseHeaderUrl = exchangeUrl+'/response/header',
	
			requestBodyUrl = exchangeUrl+'/request/body',
			requestRawUrl = exchangeUrl+'/request/raw',
			requestBodyBeautifiedUrl = '/admin/web/exchanges/'+exchangeId+'/request/body',
			requestHeaderUrl = exchangeUrl+'/request/header';

		function getHeaderTableConfiguration(url) {		
		  return {
				  'bJQueryUI': true,
				  "bFilter": false,
				  "bInfo": false,
				  "bPaginate": false,
				  "bServerSide": true,
				  "sAjaxSource": url,
				  "sAjaxDataProp": "headers",
				  "aoColumns": [
				                { "mDataProp": "name" },
				                { "mDataProp": "value" }
				              ],
		    "fnServerData": function ( sSource, aoData, fnCallback ) {
		        $.ajax( {            	  
		          "dataType": 'json', 
		          "type": "GET", 
		          "url": sSource, 
		          "data": [], 
		          "success": function(data) {   
		          	data.sEcho = aoData.sEcho;
		          	data.iTotalRecords = data.headers.length;
		          	data.iTotalDisplayRecords = data.headers.length;
		          	fnCallback(data);
		          }
		        } );
		      }		              
		  	};
		}
		
		function getMetaTableConfiguration(propsFunc) {
			return {
				  'bJQueryUI': true,
				  "bFilter": false,
				  "bInfo": false,
				  "bPaginate": false,
				  "bServerSide": true,
				  "sAjaxSource": exchangeUrl,
	  		      "fnServerData": function ( sSource, aoData, fnCallback ) {
				        $.ajax( {            	  
				          "dataType": 'json', 
				          "type": "GET", 
				          "url": sSource, 
				          "data": [], 
				          "success": function(data) {
				        	fnCallback({
				        		sEcho:aoData.sEcho,
				        		aaData: propsFunc(data),
				        		iTotalRecords:propsFunc(data).length, 
				        		iTotalDisplayRecords:propsFunc(data).length
				        	});
				          }
				        } );
				    }		              
		  	}
		}

		function loadText(selector, url) {			
			$.get(url, function(resp) {
				$(selector).text(resp);
			}, 'text');				
		}
		
		$.get(exchangeUrl, function(exc) {
			if (exc.respContentType == 'text/xml') {
				$.get(responseBodyBeautifiedUrl, function(resp) {
					$('#response-body').html(resp);
				});
			} else {
				loadText('#response-body', responseBodyUrl);
			}	
			
			if (exc.reqContentType == 'text/xml') {
				$.get(requestBodyBeautifiedUrl, function(resp) {
					$('#request-body').html(resp);
				});
			} else {
				loadText('#request-body', requestBodyUrl);
			}			
		}, 'json');

		$('#request-meta').dataTable(getMetaTableConfiguration(function(data) { return [
		   ["Time", data.time],
		   ["Method", data.method],
   		   ["Path", data.path],
		   ["Proxy", data.rule],
		   ["Client", data.client],
		   ["Content Type", data.reqContentType],
		   ["Length", data.reqContentLenght],
		];}));
		$('#request-headers').dataTable(getHeaderTableConfiguration(requestHeaderUrl));
		loadText('#request-raw', requestRawUrl);
		
		$('#response-meta').dataTable(getMetaTableConfiguration(function(data) { return [
		   ["Status Code", data.statusCode],
		   ["Server", data.server],
		   ["Content Type", data.respContentType],
		   ["Length", data.respContentLenght],
		   ["Duration", data.duration],
		];}));
		$('#response-headers').dataTable(getHeaderTableConfiguration(responseHeaderUrl));	
		loadText('#response-raw', responseRawUrl);
	}
	

	function loadProxyCallsTable(proxyName) {
		$('#proxy-calls-table').dataTable({
			  'bJQueryUI': true,
			  'sPaginationType': 'full_numbers',
			  "bProcessing": true,
			  "bServerSide": true,
			  "bDestroy":true,
			  "sAjaxSource": '/admin/rest/exchanges',
			  "sAjaxDataProp": "exchanges",
			  "aoColumnDefs": [ 
		           {
		               "fnRender": function ( o, v ) {
		                   return '<a href="/admin/call?id='+o.aData.id+'">'+v+'</a>';
		               },
		               "aTargets": [ 0 ]
		           }
			  ],
			  "aoColumns": [
			                { "mDataProp": "time" },
			                { "mDataProp": "statusCode" },
			                { "mDataProp": "method" },
			                { "mDataProp": "path" },
			                { "mDataProp": "client" },
			                { "mDataProp": "server" },
			                { "mDataProp": "reqContentType" },
			                { "mDataProp": "reqContentLenght" },
			                { "mDataProp": "respContentType" },
			                { "mDataProp": "respContentLenght" },
			                { "mDataProp": "duration" }
			              ],
	      "fnServerData": function ( sSource, aoData, fnCallback ) {
	    	  function getParam(name) {
	    		  function byName(it) {
	    			  return it.name == name;
	    		  }
	    		  return $.grep(aoData, byName)[0].value;
	    	  }
	    	  
	          $.ajax( {            	  
	            "dataType": 'json', 
	            "type": "GET", 
	            "url": sSource, 
	            "data": [{name:'proxy', value:proxyName},
	                     {name:'offset', value:getParam('iDisplayStart')}, 
	                     {name:'max', value:getParam('iDisplayLength')},
	                     {name:'sort', value:getParam('mDataProp_'+getParam('iSortCol_0'))},
	                     {name:'order', value:getParam('sSortDir_0')}], 
	            "success": function(data) {   
	            	data.sEcho = aoData.sEcho;
	            	data.iTotalRecords = data.total;
	            	data.iTotalDisplayRecords = data.total;
	            	fnCallback(data);
	            }
	          } );
	        }		              
		});		
	}
	
	return {
		loadExchange:loadExchange,
		loadProxyCallsTable:loadProxyCallsTable
	}
}();

$(function() {
	
	$('#proxy-rules-table, #interceptor-table, #statistics-table, #statuscode-table' ).dataTable({
	  'bJQueryUI': true,
	  'sPaginationType': 'full_numbers'
	});
	
	$('#fwdrules-table').dataTable({
		  'bJQueryUI': true,
		  'sPaginationType': 'full_numbers',
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": '/admin/rest/proxies',
		  "sAjaxDataProp": "proxies",
		  "aoColumns": [
		                { "mDataProp": "name" },
		                { "mDataProp": "listenPort" },
		                { "mDataProp": "virtualHost" },
		                { "mDataProp": "method" },
		                { "mDataProp": "path" },
		                { "mDataProp": "targetHost" },
		                { "mDataProp": "targetPort" },
		                { "mDataProp": "count" },
		                { "mDataProp": "actions" },
		              ],
		  "aoColumnDefs": [ 
	           {
	               "fnRender": function ( o, v ) {
	                   return '<a href="'+o.aData.details+'">'+v+'</a>';
	               },
	               "aTargets": [ 0 ]
	           },
	           {
	               "fnRender": function ( o, v ) {
	            	   if (v['delete']) {
	            		   return '<a href="'+v['delete']+'"><span class="ui-icon ui-icon-trash"></span></a>';
	            	   }
	                   return '';
	               },
	               "aTargets": [ 8 ]
	           }
		  ],
         "fnServerData": function ( sSource, aoData, fnCallback ) {
      	  function getParam(name) {
      		  function byName(it) {
      			  return it.name == name;
      		  }
      		  return $.grep(aoData, byName)[0].value;
      	  }
      	  
            $.ajax( {            	  
              "dataType": 'json', 
              "type": "GET", 
              "url": sSource, 
              "data": [{name:'offset', value:getParam('iDisplayStart')}, 
                       {name:'max', value:getParam('iDisplayLength')},
                       {name:'sort', value:getParam('mDataProp_'+getParam('iSortCol_0'))},
                       {name:'order', value:getParam('sSortDir_0')}], 
              "success": function(data) {   
              	data.sEcho = aoData.sEcho;
              	data.iTotalRecords = data.total;
              	data.iTotalDisplayRecords = data.total;
              	fnCallback(data);
              }
            } );
          }		              
	});

	$('#message-stat-table').dataTable({
		  'bJQueryUI': true,
		  'sPaginationType': 'full_numbers',
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": '/admin/rest/exchanges',
		  "sAjaxDataProp": "exchanges",
		  "aoColumnDefs": [ 
	           {
	               "fnRender": function ( o, v ) {
	                   return '<a href="/admin/call?id='+o.aData.id+'">'+v+'</a>';
	               },
	               "aTargets": [ 0 ]
	           }
		  ],
		  "aoColumns": [
		                { "mDataProp": "time" },
		                { "mDataProp": "statusCode" },
		                { "mDataProp": "rule" },
		                { "mDataProp": "method" },
		                { "mDataProp": "path" },
		                { "mDataProp": "client" },
		                { "mDataProp": "server" },
		                { "mDataProp": "reqContentType" },
		                { "mDataProp": "reqContentLenght" },
		                { "mDataProp": "respContentType" },
		                { "mDataProp": "respContentLenght" },
		                { "mDataProp": "duration" }
		              ],
          "fnServerData": function ( sSource, aoData, fnCallback ) {
        	  function getParam(name) {
        		  function byName(it) {
        			  return it.name == name;
        		  }
        		  return $.grep(aoData, byName)[0].value;
        	  }
        	  
              $.ajax( {            	  
                "dataType": 'json', 
                "type": "GET", 
                "url": sSource, 
                "data": [{name:'offset', value:getParam('iDisplayStart')}, 
                         {name:'max', value:getParam('iDisplayLength')},
                         {name:'sort', value:getParam('mDataProp_'+getParam('iSortCol_0'))},
                         {name:'order', value:getParam('sSortDir_0')}], 
                "success": function(data) {   
                	data.sEcho = aoData.sEcho;
                	data.iTotalRecords = data.total;
                	data.iTotalDisplayRecords = data.total;
                	fnCallback(data);
                }
              } );
            }		              
	});
			
	$('#tabs li').hover(
    	    function () {
    	    	    $(this).addClass("ui-state-hover");
    	    },
    	    function () {
    	    	    $(this).removeClass("ui-state-hover");
    	    }
    );
    
    $('.mb-button').button();
    
    $('form').validationEngine('attach', {promptPosition : 'bottomRight', scroll: false});
    $('form').submit(function() {
		return this.validationEngine('validate');
    });			
});
