console = { 
    log: print,
    warn: print,
    error: print
};

console.log("begin");

print_event = function(event) {
	console.log(event.getName() + " " + event.getPrevTid() + " -> " + event.getNextTid());
}

var result = { count: 0.0 };

var handler = new Object();
handler.handleEvent = function(event) {
	result.count += 1;
}

var module = new Object();
module.process = function(trace) {
	while(trace.hasNext()) {
		var event = trace.getNext();
		result.count += 1;
	}
}

console.log("end");