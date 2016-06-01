load('https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.13.0/moment.min.js');

var convert = function(data){
    
    return {
        createdAt: moment.utc(data.createdAt).format("YYYY-MM-DD"),
        updatedAt: moment.utc(data.updatedAt).format("YYYY-MM-DD"),
        state: data.state,
        customer: data.customer_url.split("/")[3],
        items_url: data.items_url
    };
}