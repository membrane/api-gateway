$(function() {
	$('table.display').dataTable({
	  'bJQueryUI': true,
	  'sPaginationType': 'full_numbers'
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
