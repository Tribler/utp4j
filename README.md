#utp4j - Micro Transport Protocol for Java

[uTP][tp] is an implementation of the [LEDBAT][ledbat] algorithm. uTP is a BitTorrent 
standard and build on top of UDP, as such, it implements its own mechanisms for reliable ordered 
delivery. utp4J is (still :( ) an experimental uTP implementation in Java, currently only suited for scientific purposes. I wrote this library as a part of my Bsc thesis at the University of Zurich and i'd like to continue developing it with the intention of releasing a stable version some day. 

## Interface
utp4j follows the same approach like java.nio. If you ever have worked with nio, you will be familiar with utp4j soon. Developers only need to know about a few classes in order to use utp4j efficiently. 
A set of semantically clear methods is provided. utp4j is aims to be asynchronous and most operations return handy future objects. 

## Features
* LEDBAT algorithm
* Dynamic RTO 
* Clock drift correction
* Async Execution
* Listener interface on some futures
* Extended logging & Automated test executor

## Usage
To open up a connection:
```
UtpSocketChannel c = UtpSocketChannel.open();
c.bind(port);
UtpConnectFuture cFut = c.connect(address);

// ... You either wait...
cFut.block()...
c.write(buffer);

// ...or do something else...

if (cFut.isDone()) {
	c.write(). 
}
```
The same on the server side:
```
UtpAcceptFuture accept = server.accept();

accept.block();
// ... block on the accept future
UtpSocketChannel channel = accept.getChannel();
UtpReadFuture rFut = channel.read(buffer);

rFut.block();
// wait for the completition of the operation. 
print(rFut.getBuffer());
```
## Experimenting
There are a lot of research papers regarding LEDBAT. Because it is still an experimental algorithm, utp4j can become handy for those who are investigating delay based congestion control mechanisms. The example package contains an automated test executor. Different parameters can be specified in a CSV file. The experiments can run stable for days if desierd (depends on configuration, though). Extended logging for most of the state variables can be enabled to analyze LEDBAT behaviour. A bash script is available to quickly plot the results and can be modified if desired.

##Current Flaws
* Correct handling of RST packets 
* Closing connection while sending/reading is not yet handled good enough
* High CPU consumption
* Probably some minor bugs.

##Appreciation 
I'd like to thank all members of the [Communication Systems Group][csg] at the University Zurich for the opportunity, the provided test hardware, the room and patience.
Special thanks to Thomas and Danni for all the constructive feedback, advices and tipps. 

## License
utp4j is licensed under the Apache 2.0 [license]. 

[csg]: http://www.csg.uzh.ch/
[tp]: http://www.bittorrent.org/beps/bep_0029.html
[ledbat]: http://datatracker.ietf.org/wg/ledbat/charter/
[license]: http://www.apache.org/licenses/LICENSE-2.0.html
