$(function() {
	$('table.display').dataTable({
	  'bJQueryUI': true,
	  'sPaginationType': 'full_numbers'
	});
	
	$('#message-stat-table').dataTable({
		  'bJQueryUI': true,
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": '../statistics',
		  "sAjaxDataProp": "statistics",
		  "aoColumns": [
		                { "mDataProp": "statusCode" },
		                { "mDataProp": "time" },
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
        	  console.log(aoData);
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

	/*	
	$('table.sortPrio').dataTable({
	  'bJQueryUI': true,
	  'sPaginationType': 'full_numbers',
	  'aaSorting':[[ 1, "desc" ]]
	});
	*/
	
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
