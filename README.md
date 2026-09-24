<h1>Redis Lite (Java)</h1>

<h2>Description</h2>
A lightweight Redis clone built in Java. RESP protocol-compatible (connect with redis-cli), multi-threaded server, with a ConcurrentHashMap-backed store and linked-list-powered list operations, including recent commands like LMOVEM.<br />


<h2>Languages and Utilities Used</h2>

- <b>Java</b> 

<h2>Environments Used </h2>

- <b>Windows 11</b> 

## Supported Commands

| Command  | Description |
|----------|-------------|
| `GET`    | Retrieve the value of a key |
| `GETRANGE` | Returns a substring based on a given start index and end index (inclusive) |
| `SET`    | Set the value of a key |
| `EXISTS` | Check whether a key exists |
| `INCR` | Increments a value by 1 |
| `DECR` | Decrements a value by 1 |
| `DEL` | Deletes one or more values from the database |
| `LCS` | Returns the length of the longest common subsequence between two strings |
| `LRANGE` | Returns the elements of a list based on a given start and end index (inclusive) |
| `LPUSH` | Pushes one or more elements to the head of a list |
| `RPUSH` | Pushes one or more elements to the tail of a list |
| `LMOVE` | Moves one element from a source list to a destination list |
| `LMOVEM` | Moves one or more elements from a source list to a destination list |
| `LLEN` | Returns the length of a list |

<h2>Program walk-through:</h2>
...

<!--
 ```diff
- text in red
+ text in green
! text in orange
# text in gray
@@ text in purple (and bold)@@
```
--!>
