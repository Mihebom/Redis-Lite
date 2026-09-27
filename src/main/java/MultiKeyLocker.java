import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


//Used for instances when more than TWO keys need to be claimed by a thread at a given time
public class MultiKeyLocker {

    final ArrayList<ReentrantLock> acquiredLocks = new ArrayList<>();
    final ConcurrentHashMap<String, ReentrantLock> keyLocks;

    MultiKeyLocker(ConcurrentHashMap<String, ReentrantLock> keyLocks){
        this.keyLocks = keyLocks;
    }

    public void lockKeys(String[] sortedKeys){

        for (String sortedKey : sortedKeys) {

            ReentrantLock lock = keyLocks.computeIfAbsent(sortedKey, k -> new ReentrantLock());
            lock.lock();
            System.out.println("Successfully locked");
            acquiredLocks.add(lock);
        }


    }

    public void unlockKeys(){

        for(ReentrantLock lock : acquiredLocks){
            lock.unlock();
            System.out.println("Successfully unlocked");
        }
    }

}
