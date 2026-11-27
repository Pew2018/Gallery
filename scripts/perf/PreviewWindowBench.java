import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Host-only work measurement for the Photos first-slice cache filter.
 *
 * It is NOT device timing: it creates 4,993 real files, warms the page cache and
 * compares a full stat() pass against the bounded 160-row window. Run with:
 *   javac scripts/perf/PreviewWindowBench.java && java -cp scripts/perf PreviewWindowBench
 */
public class PreviewWindowBench {
    static class Item { String path; long taken; int idx; Item(String p,long t,int i){path=p;taken=t;idx=i;} }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("preview-bench").toFile();
        int n = 4993;
        List<Item> items = new ArrayList<>(n);
        for (int i=0;i<n;i++) {
            File f = new File(root, "photo-"+i+".jpg");
            Files.write(f.toPath(), new byte[]{(byte)i});
            items.add(new Item(f.getAbsolutePath(), 1_000_000L - i, i));
        }
        // warm page cache
        for (int r=0;r<2;r++) fullFilter(items);
        long[] full = new long[5], bounded = new long[5], sort = new long[5];
        for (int r=0;r<5;r++) {
            long t0=System.nanoTime(); int kept=fullFilter(items); full[r]=System.nanoTime()-t0;
            t0=System.nanoTime(); int win=boundedSelect(items,160); bounded[r]=System.nanoTime()-t0;
            List<Item> copy = new ArrayList<>(items);
            t0=System.nanoTime(); copy.sort((a,b)-> a.taken!=b.taken?Long.compare(b.taken,a.taken):a.path.compareTo(b.path)); sort[r]=System.nanoTime()-t0;
            if(r==0) System.out.println("kept="+kept+" window="+win);
        }
        System.out.printf(Locale.US, "full_filter_4993_median_ms=%.2f%n", median(full));
        System.out.printf(Locale.US, "bounded_window_160_median_ms=%.2f%n", median(bounded));
        System.out.printf(Locale.US, "full_sort_4993_median_ms=%.2f%n", median(sort));
        for (File f : root.listFiles()) f.delete(); root.delete();
    }
    static int fullFilter(List<Item> items) {
        int kept=0;
        for (Item it: items) {
            File f=new File(it.path); File p=f.getParentFile();
            boolean ok = p!=null && p.isDirectory() && p.canRead();
            if (!ok || f.isFile()) kept++;
        }
        return kept;
    }
    static int boundedSelect(List<Item> items,int limit) {
        int kept=0, i=0;
        while(i<items.size() && kept<limit) {
            Item it=items.get(i++); File f=new File(it.path); File p=f.getParentFile();
            boolean ok = p!=null && p.isDirectory() && p.canRead();
            if (!ok || f.isFile()) kept++;
        }
        return kept;
    }
    static double median(long[] a){ long[] b=a.clone(); Arrays.sort(b); return b[b.length/2]/1_000_000.0; }
}
