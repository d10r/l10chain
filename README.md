# l10chain

## Rationale

This project was about learning Clojure and getting a better understanding of how a Blockchain works.
I tried to keep it as simple as possible yet covering all aspects such that it can be considered a complete Blockchain implementation.

Inspiration: [Naivechain](https://medium.com/@lhartikk/a-blockchain-in-200-lines-of-code-963cc1cc0e54#.v23r0k1r5), Mario's nice comments about Clojure.

## Design

The basic design is derived from Bitcoin.
Connected nodes create a distributed ledger which documents transactions and consists of immutable blocks with a defined format.
Blocks have a one-dimensional, unambiguous order are chained by using cryptographic hash functions, similar to the git VCS.
Unlike Bitcoin, consensus is not achieved via a Proof of Work algorithm, but with a kind of Proof of Stake with nearly deterministic random beacon (see *Consensus*).
Nodes have one address which is an ECDSA public key. The corresponsing private key is used for block and transaction signing.

### Consensus

The consensus algo is inspired by [dfinity](http://dfinity.network) and [Algorand](https://arxiv.org/pdf/1607.01341.pdf) (I'm not sure which of them was first).
It takes the idea of a *random beacon* in a simplified way.
This random beacon is defined such that the forger has no influence on it. The produced beacon signature at this point is determinstic and can't be influenced by the forger varying block data (e.g. adding or leaving out a transaction).
That's achieved by creating the signature only over the beacon signature of the previous block and the height of the current block.
The only influence a forging node has to either create and broadcast a candidate block or not.
ECDSA signatures are by default not deterministic. Unlike with RSA (RSASSA-PKCS1-v1_5), simple padding is not allowed - the padding needs to be random.
However by using a cryptographic hash of the plaintext for the padding, deterministic signatures are possible anyway, see [RFC 6979](https://tools.ietf.org/html/rfc6979).
Daniel W. pointed me to his implementation of this RFC in [Mycelium Wallet](https://github.com/mycelium-com/wallet/blob/fb12ac9d9149b12ecc5a50694e6815b9d11adca4/bitlib/src/main/java/com/mrd/bitlib/crypto/InMemoryPrivateKey.java#L227).
TODO: implement this.

Via a distance function, a priority ordered list of candidate forgers can be derived from the random beacon.
During the timeslot for the next block (determined by blocktime), every node can calculate this list by calculating the distance for every candidate forger based on the previous signature.
When receiving a block, nodes check if according to this list it has higher priority than the block currently held for the open timeslot (this may be the self forged block or a block received before).
If this new block is *better* in the described way, the node replaces the currently hold one.
A received block is relayed only if it's the best one the node has seen so far for the current timeslot.
If a block with height not corresponding to the current timeslot is received, it's not relayed (even if better).
It's thus important for nodes to have the clock set correctly, otherwise they will easily get out of sync and frequently create short term forks.

This design has the property that the priority ordered list of candidate forgers is always publicly known for a brief period of time (blocktime), but unknown before and unknown for later blocks.
It doesn't take the approach of limiting attack surface as far as Algorand. In Algorand, the priority of a forging node becomes visible only after a node broadcastet a block, making it impossible to bribe or censor (e.g. via DOS) that node.
An advantage of this compromise is that nodes are able to exactly know where in the list a received block is. That means e.g. if a block is received from the forger on top of the list, a node knows that it doesn't need to look any further, basically leading to instant finality for that block (assuming that the node is on the correct chain).
It also allows for some optimizations like timing broadcasting of blocks according to the position in the list.
Also, it allows for a less complex mechanism. Due to the limited knowledge of nodes, Algorand additionally needs a Byzantine agreement protocol for every block in order to determine which of the broadcastet blocks is going to be included.

The probability of the 
If the network grows bigger, the probability of this will diminish. This could be countered by designing a check-in mechanism which requires potential forgers to explicitly state there wish to be active forgers. A penalty on those failing to fulfill that promise could incentivize forgers to check out before going offline.

When a node receives a block with mismatching previous hash, it will request more blocks from that node. On receiving them, the node will check if those blocks lead to a better chain. This enables reorgs.
A chain is better if it weights more.
The weight is defined by the inverse of average difference. Missing blocks are penalized, making long range attacks very difficult.

Only addresses with min 1 coin balance are allowed to forge blocks. This avoids sybil attacks.
Of course an actor having more than one coin can add as many nodes as there are coins. Thus the influence on the network can be proportional to the economic power, just as with PoW or PoS.

Forgers get a block reward of 1 coin, thus have an incentive to create and broadcast blocks.

### Block anatomy

I started from the Bitcoin block layout. For the sake of simplicity, the tx hash is created by just concatenating all transactions instead of using a Merkle tree.
Additionally fields:
* forger: address of the block creator
* beaconsig: The signature representing the random beacon
* hashsig: Signature of the block hash

Forger is the public key for verifying the signatures.
The beacon signature is needed for consensus finding.
The hash signature links the rest of the Block. Without it, an attacker could make arbitrary changes to the block and broadcast it, abusing the forger's signature.

TODO: Currently, there's only one signature implemented which corresponds to what's described here as *beaconsig*.

Block reward isn't explicitly modeled as transaction.

### Protocol

Supports the basic operations needed for broadcasting and active syncing.

### Networking

Uses Websocket connections for the P2P protocol.
Nodes may or may not establish a listening socket.
At least one node with listening socket needs to be running (bootstrapping node). Other nodes 

### Usage

Accounts are created with a shell script. Requires openssl installed.
Example: `./generate_ec_keypair.sh data`

4 accounts are already pre-generated in directories named node[0-3].

Start an instance with the account from directory node0 which generates a genesis block, listens for p2p node connections on the default port and starts a forger process.
`lein run -g -s -f`

Start an instance with the account from directory node1 which connects to the instance above and just passively builds its chain from what it receives.
`lein run -d node1 --peers localhost`

Start an instance with the account from directory node2 which connects to the instance above and also starts forging.
`lein run -d node2 --peers localhost -f`

Since there's no RPC interface yet, the way to interact with a running node is via the REPL.
`lein repl`
Note that this doesn't automatically execute the main function. 

### Missing

Missing essential parts: 

* More tests
* Rename the *signature* field in block to *beaconsig* and add the *headersig* field. Without that, an attacker can easily create manipulated blocks, abusing valid beacon signatures.
* Transactions need to be unique in order to avoid re-application by mistake or by purpose. With non-deterministic signatures that may not be an issue (?). Even then, it may be desirable to have a deterministic order of related transactions. Ethereum for example has a nonce field which gives transactions an order per sender.
* Persistence: possibility to write chain state to and read from disk
* Node discovery: Add a protocol command for retrieving a list of known nodes/peers.
* Implement message (txns and blocks) relaying: requires managing a list of peers per message in order to limit redundancy and avoid amplification attacks.

### Next steps

Some other desirable parts / features:

* Allow fractional currency units (fixed point)
* Finality checking: by holding a list of the hashes of eligible forgers, a node can tell if a candidate block is the best one. There remains however the current chain is a losing fork.
* Optimized block broadcasting: clients could be aware of their priority for the current slot and time broadcasting of their candidate block accordingly. E.g. if blocks of top priority forgers are broadcastet early on, blocks of low priority forgers won't be broadcast in the first place, leading to less overhead p2p traffic and short term forks. 
* Add transaction fees, prioritize inclusion of transactions in blocks by fee
* Integrate account creation (is currently done by standalone bash script)
* Switch to base58 representation of addresses
* Test the limits of fault tolerance: how do faulty nodes, bad clocks, network delays etc. impact the forking rate
* Test scalability: how do the number of nodes, number of transactions, size of chain etc. impact performance?
* Web-of-trust like permission model: try more sophisticated criteria for forging blocks. E.g. make it dependent on prior communication behaviour, using a generation model of nodes etc.
* *Grow* money distributed accross nodes instead of issuing it via block reward. Does that need explicit increments in discrete steps or could it somehow be implemented by replacing constant values with a function of time (and other variables?)?
* Investigate what would be needed for light clients.
* RPC interface for communication with the node
