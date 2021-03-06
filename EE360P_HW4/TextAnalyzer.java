import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

// Do not change the signature of this class
public class TextAnalyzer extends Configured implements Tool {

    // Replace "?" with your own output key / value types
    // The four template data types are:
    //     <Input Key Type, Input Value Type, Output Key Type, Output Value Type>
    public static class TextMapper extends Mapper<LongWritable, Text, Text, MapWritable> {

        private Text word = new Text();
        private MapWritable mapWritable = new MapWritable();

        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            // Implementation of you mapper function
            HashMap<String, Integer> lineMap = new HashMap<>();
            String line = value.toString().replaceAll("[^a-zA-Z0-9]+", " ");

            StringTokenizer stok = new StringTokenizer(line);
            while(stok.hasMoreTokens()) {
                String token = stok.nextToken().toLowerCase();
                if(lineMap.containsKey(token)) {
                    lineMap.put(token, lineMap.get(token) + 1);
                } else lineMap.put(token, 1);
            }

            for(Map.Entry<String, Integer> entry : lineMap.entrySet()) {
                mapWritable.put(new Text(entry.getKey()), new IntWritable(entry.getValue()));
            }

            for(String keyToken : lineMap.keySet()) {
                word.set(keyToken);
                /*mapWritable.put(new Text(keyToken),
                        new IntWritable(((IntWritable) mapWritable.get(new Text(keyToken))).get() - 1));*/
                context.write(word, mapWritable);
            }
        }

    }

    // Replace "?" with your own key / value types
    // NOTE: combiner's output key / value types have to be the same as those of mapper
    public static class TextCombiner extends Reducer<Text, MapWritable, Text, MapWritable> {

        private MapWritable combiner = new MapWritable();
        private Text word = new Text();

        public void reduce(Text key, Iterable<MapWritable> tuples, Context context)
                throws IOException, InterruptedException {
            // Implementation of you combiner function
            HashMap<String, Integer> comboMap = new HashMap<>();

            //int sum = 0;
            for (MapWritable mapWritable : tuples)  {
                for(Map.Entry<Writable, Writable> entry : mapWritable.entrySet()) {
                    String keyword = entry.getKey().toString();
                    if(comboMap.containsKey(keyword)) {
                        int count = ((IntWritable) entry.getValue()).get();
                        comboMap.put(keyword, comboMap.get(keyword) + count);
                    } else {
                        comboMap.put(keyword, ((IntWritable) entry.getValue()).get());
                    }
                }
            }

            for(Map.Entry<String, Integer> entry : comboMap.entrySet()) {
                combiner.put(new Text(entry.getKey()), new IntWritable(entry.getValue()));
            }

            for(String keyToken : comboMap.keySet()) {
                word.set(keyToken);
                /*mapWritable.put(new Text(keyToken),
                        new IntWritable(((IntWritable) mapWritable.get(new Text(keyToken))).get() - 1));*/
                context.write(word, combiner);
            }
        }
    }

    // Replace "?" with your own input key / value types, i.e., the output
    // key / value types of your mapper function
    public static class TextReducer extends Reducer<Text, MapWritable, Text, Text> {
        private final static Text emptyText = new Text("");
        private Text queryWordText = new Text();

        public void reduce(Text key, Iterable<MapWritable> queryTuples, Context context)
                throws IOException, InterruptedException {
            // Implementation of your reducer function
            HashMap<String, Integer> map = new HashMap<>();

            //int sum = 0;
            for (MapWritable mapWritable : queryTuples)  {
                for(Map.Entry<Writable, Writable> entry : mapWritable.entrySet()) {
                    String keyword = entry.getKey().toString();
                    if(map.containsKey(keyword)) {
                        int count = ((IntWritable) entry.getValue()).get();
                        map.put(keyword, map.get(keyword) + count);
                    } else {
                        map.put(keyword, ((IntWritable) entry.getValue()).get());
                    }
                }
            }
            try {
                map.put(key.toString(), map.get(key.toString()) - 1);
            } catch (Exception e) {
                //somehow key doesn't exist in map
            }

            //find max value
            int max = 0;
            for(String mapkey: map.keySet()) {
                int value = map.get(mapkey);
                if(value > max) {
                    max = value;
                }
            }

            context.write(key, new Text(String.valueOf(max)));

            // Write out the results; you may change the following example
            // code to fit with your reducer function.
            //   Write out the current context key
            //context.write(key, emptyText);
            //   Write out query words and their count
            for(String queryWord: map.keySet()){
                String count = map.get(queryWord).toString() + ">";
                queryWordText.set("<" + queryWord + ",");
                context.write(queryWordText, new Text(count));
            }
            //   Empty line for ending the current context key
            context.write(emptyText, emptyText);
        }
    }

    public int run(String[] args) throws Exception {
        Configuration conf = this.getConf();

        // Create job
        Job job = new Job(conf, "EID1_EID2"); // Replace with your EIDs
        job.setJarByClass(TextAnalyzer.class);

        // Setup MapReduce job
        job.setMapperClass(TextMapper.class);
        //   Uncomment the following line if you want to use Combiner class
        // job.setCombinerClass(TextCombiner.class);
        job.setReducerClass(TextReducer.class);

        // Specify key / value types (Don't change them for the purpose of this assignment)
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        //   If your mapper and combiner's  output types are different from Text.class,
        //   then uncomment the following lines to specify the data types.
        //job.setMapOutputKeyClass(?.class);
        //job.setMapOutputValueClass(?.class);

        // Input
        FileInputFormat.addInputPath(job, new Path(args[0]));
        job.setInputFormatClass(TextInputFormat.class);

        // Output
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        job.setOutputFormatClass(TextOutputFormat.class);

        // Execute job and return status
        return job.waitForCompletion(true) ? 0 : 1;
    }

    // Do not modify the main method
    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(new Configuration(), new TextAnalyzer(), args);
        System.exit(res);
    }

    // You may define sub-classes here. Example:
    // public static class MyClass {
    //
    // }
}



