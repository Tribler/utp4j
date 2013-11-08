#utp4j - Micro Transport Protocol for Java

[uTP][tp] is a implementation of the LEDBAT algorithm [LEDBAT][ledbat]. uTP is a BitTorrent 
standard and build on top of UDP, as such, it implements its own mechanisms for reliable ordered 
delivery. utp4J is an experimental implementation in Java of uTP for scientific purposes. 

## Interface
utp4j follows the same approach like java.nio. If you have ever worked with java.nio, you can use 
utp4j too. Developers only need to know about a few classes in order to use utp4j efficiently. 
A set of semantically clear methods is provided. However, utp4j is asynchronous as it works with 
future objects but is not thread save.

## Features
* Dynamic RTO 
* Clock drift correction
* Async Execution
* Listener interface some futures
* Extended logging
* Automated test executor 

## Usage
To open up a connection:
UtpSocketChannel c = UtpSocketChannel.open();
c.bind(port);
UtpConnectFuture cFut = c.connect(address);
...
You either wait...

cFut.block()...
c.write(buffer);

or do something else...
...
if (cFut.isDone()) {
	c.write(). 
}

The same on the server side:

UtpAcceptFuture accept = server.accept();

accept.block();
... block on the accept future
UtpSocketChannel channel = accept.getChannel();
UtpReadFuture rFut = channel.read(buffer);

rFut.block();
wait for the completition of the operation. 
print(rFut.getBuffer());

## Experimenting
See the example testplan.csv in the thestPlan folder. Configure the ConfigTestPlanReader. 
All defined parameters will be processed and logged. 

## License
utp4j is licensed under the Apache 2.0 [license]. 


[tp]: http://www.bittorrent.org/beps/bep_0029.html
[ledbat]: : http://datatracker.ietf.org/wg/ledbat/charter/
[license]: http://www.apache.org/licenses/LICENSE-2.0.html