!!! Note
This is totally work in progress. Readme not in sync with the code. Tests and some cleanup missing.
Instructions for how to run it will follow. 

# l10chain

## Rationale

This project was about learning Clojure and getting a better understanding of how a Blockchain works.
I tried to keep it as simple as possible yet covering all aspects such that it can be considered a complete Blockchain implementation.

Inspiration: https://medium.com/@lhartikk/a-blockchain-in-200-lines-of-code-963cc1cc0e54#.v23r0k1r5, Mario

### General

Uses (TODO) RSA crypto because deterministic signatures are needed (which is easier with RSA). Reason: see chapter *Consensus*.

### Consensus

The consensus algo is inspired by dfinity. It takes the idea of a *random beacon*, but simplifies it to a single signature.
This random beacon is defined such that the forger has no influence on it. She can decide to create it or not, that's it.
It's created by concatenating the previous beacon and the current block height and signing it.
RSA crypto was chosen because it makes it easier to get deterministic signatures.
TODO: check that the implementation accepts only deterministic signatures. (RSASSA-PKCS1-v1_5)

Via a distance function, the random beacon determines a priority ordered list of who's supposed to forge the next block.
During the timeslot for the next block (determined by blocktime), every forger can calculate her own distance by diffing the previous block's signature with the own signature for the current block.
The node builds a block, signs and broadcasts it.
When receiving a block, nodes check if it is less distant then the block currently held for the open timeslot (this may be the self forged block or a block received before).
If this new block is *better* in the described way, the node replaces the currently hold one.
A received block is relayed only if it's better than all received before for the timeslot.
Relaying stops as soon as the timeslot is over. It's thus important for nodes to have the clock set correctly, otherwise they will easily get out of sync.

This design has the property that the information about how the order of preferred forgers is always limited to the next block.
It doesn't take that approach as far as Algorand, where before the broadcasting of the actual block by a forger it's not possible for other nodes to determine who will forge the next block.
On the other hand it allows nodes to know where in the ordered list a forger is placed for the current block. Which implies that if a block is broadcastet by the forger on top of the list, we have instant finality, something the Algorand design needs a Byzantine agreement protocol for on top of it.
Of course this advantage stands only if the best forger does forge and broadcast a block. The block rewards incentivices this.
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

Block reward isn't explicitly modeled as transaction.

### Protocol

Supports the basic operations needed for broadcasting and active syncing.

### Networking

Uses Websocket connections for the P2P protocol.

### Usage

Accounts are created with a shell scripts. Requires openssl installed.
Currently, the only way to control a node once it's running is via Clojure REPL.