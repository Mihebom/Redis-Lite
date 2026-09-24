<h1>Redis Lite (Java)</h1>

<h2>Description</h2>
<br>A lightweight Redis clone built in Java. RESP protocol-compatible (connect with redis-cli), multi-threaded server, with a ConcurrentHashMap-backed store and linked-list-powered list operations, including recent commands like LMOVEM.<br />

## Supported Commands

| Command  | Description |
|----------|-------------|
| `PING` | Returns pong - used to test client-server responsiveness |
| `ECHO` | Returns given user input |
| `GET`    | Retrieve the value of a key |
| `GETRANGE` | Returns a substring based on a given start index and end index (inclusive) |
| `SET`    | Set the value of a key (**See optional arguments Below**)|
| `EXISTS` | Check whether a key exists |
| `INCR` | Increments a value by 1 |
| `DECR` | Decrements a value by 1 |
| `DEL` | Deletes one or more values from the database |
| `LCS` | Returns the length of the longest common subsequence between two strings |
| `LRANGE` | Returns the elements of a list based on a given start and end index (inclusive) |
| `LPUSH` | Pushes one or more elements to the head of a list |
| `RPUSH` | Pushes one or more elements to the tail of a list |
| `LMOVE` | Moves one element from a source list to a destination list |
| `LMOVEM` | Moves one or more elements from a source list to a destination list (**See optional arguments Below**)|
| `LLEN` | Returns the length of a list |

## Supported Optional Commands

### SET

| Command  | Description |
|----------|-------------|
| `EX seconds` | Set key expiry in seconds (must be a positive integer) |
| `PX milliseconds` | Set key expiry in milliseconds (must be a positive integer) |
| `EXAT unix-time-seconds` | Returns the length of a list |
| `PXAT unix-time-milliseconds` | Returns the length of a list |
### LMOVEM
| Command  | Description |
|----------|-------------|
| `COUNT count` | Moves up n times. if n is larger than the length of source, all items are moved |
| `EXACTLY exactly` | Moves up exactly n times. If the length of the source is less than n, no operation is performed |
| `OBO` | Moves elements from source to destination one at a time i.e. pop and push |
| `BULK` | Moves all elements at once keeping their relative order |

## Build & Run

```bash
# Clone the repo
git clone https://github.com/Mihebom/Redis-Lite.git
cd <your-repo>

# Build
mvn compile

# Run
mvn exec:java
```

By default the server starts on port `6379` , so you can connect immediately with `redis-cli`.

## Connecting the redis-cli

```bash
#Connect client to Server
redis-cli -p 6379

# Example commands and responses
127.0.0.1:6379> SET foo bar
OK
127.0.0.1:6379> GET foo
"bar"
127.0.0.1:6379> RPUSH mylist a b c
(integer) 3
127.0.0.1:6379> LMOVEM mylist mylist2 LEFT RIGHT
1) "a"
```

## Architecture

- **Storage layer**: a single `ConcurrentHashMap<String, Value>` acts as the database, giving thread-safe access without a global lock.
- **List type**: values for list keys are stored as linked lists, supporting push/pop/move-style operations efficiently at both ends.
- **Networking**: a multi-threaded server that accepts multiple client connections, parses and responds using the RESP (REdis Serialization Protocol) format.

## Roadmap / Ideas

- [ ] Add support for more data types (hashes, sets, sorted sets)
- [ ] Add more commands
- [ ] Improve database persistence
- [ ] Improve server efficiency

<!--
 ```diff
- text in red
+ text in green
! text in orange
# text in gray
@@ text in purple (and bold)@@
```
--!>
