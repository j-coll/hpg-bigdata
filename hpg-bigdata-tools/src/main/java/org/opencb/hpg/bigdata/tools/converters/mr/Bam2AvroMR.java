/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.hpg.bigdata.tools.converters.mr;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;

import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapreduce.AvroJob;
import org.apache.avro.mapreduce.AvroKeyOutputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.ga4gh.models.ReadAlignment;
import org.opencb.hpg.bigdata.core.converters.SAMRecord2ReadAlignmentConverter;
import org.opencb.hpg.bigdata.tools.io.RegionDepthWritable;
import org.opencb.hpg.bigdata.tools.utils.CompressionUtils;
import org.seqdoop.hadoop_bam.AnySAMInputFormat;
import org.seqdoop.hadoop_bam.SAMRecordWritable;
import org.seqdoop.hadoop_bam.util.WrapSeekable;

public class Bam2AvroMR {

	public static class Bam2GaMapper extends Mapper<LongWritable, SAMRecordWritable, ChunkKey, SAMRecordWritable> {
		@Override
		public void map(LongWritable key, SAMRecordWritable value, Context context) throws IOException, InterruptedException {
			ChunkKey newKey;
			
			SAMRecord sam = value.get();
			if (sam.getReadUnmappedFlag()) {
				newKey = new ChunkKey(new String("*"), (long) 0);
			} else {
				long start_chunk = sam.getAlignmentStart() / RegionDepthWritable.CHUNK_SIZE;
				long end_chunk = sam.getAlignmentEnd() / RegionDepthWritable.CHUNK_SIZE;
				newKey = new ChunkKey(sam.getReferenceName(), start_chunk);
				
				context.write(newKey, value);
			}
		}
	}

	public static class Bam2GaReducer extends Reducer<ChunkKey, SAMRecordWritable, AvroKey<ReadAlignment>, NullWritable> {

		public void reduce(ChunkKey key, Iterable<SAMRecordWritable> values, Context context) throws IOException, InterruptedException {
			SAMRecord sam;
			SAMRecord2ReadAlignmentConverter converter = new SAMRecord2ReadAlignmentConverter();
			
			for (SAMRecordWritable value : values) {
				sam = value.get();
				sam.setReferenceName(key.getName());
				sam.setMateReferenceName(context.getConfiguration().get("" + sam.getMateReferenceIndex()));
				ReadAlignment readAlignment = converter.forward(sam);
				context.write(new AvroKey<ReadAlignment>(readAlignment), NullWritable.get());
			}
		}
	}
	
	public static int run(String input, String output, String codecName) throws Exception {
		Configuration conf = new Configuration();

		{
			// read header, and save sequence index/name in config 
			
			final Path p = new Path(input);
			final SeekableStream sam = WrapSeekable.openPath(conf, p);
			final SAMFileReader reader = new SAMFileReader(sam, false);
			final SAMFileHeader header = reader.getFileHeader();
			int i = 0;
			SAMSequenceRecord sr;
			while ((sr = header.getSequence(i)) != null) {
				conf.set("" + i, sr.getSequenceName());
				i++;
			}			
		}
		
		Job job = Job.getInstance(conf, "Bam2AvroMR");
		job.setJarByClass(Bam2AvroMR.class);

		// We call setOutputSchema first so we can override the configuration
		// parameters it sets
		AvroJob.setOutputKeySchema(job, ReadAlignment.getClassSchema());
		job.setOutputValueClass(NullWritable.class);
				
		// point to input data
		FileInputFormat.setInputPaths(job, new Path(input));
		job.setInputFormatClass(AnySAMInputFormat.class);
		
		// set the output format
		FileOutputFormat.setOutputPath(job, new Path(output));
		if (codecName != null) {
			FileOutputFormat.setCompressOutput(job, true);
			FileOutputFormat.setOutputCompressorClass(job, CompressionUtils.getHadoopCodec(codecName));
		}
		
		job.setMapOutputKeyClass(ChunkKey.class);
		job.setMapOutputValueClass(SAMRecordWritable.class);
        job.setOutputFormatClass(AvroKeyOutputFormat.class);

		
/*		
		job.setOutputFormatClass(AvroParquetOutputFormat.class);
		AvroParquetOutputFormat.setOutputPath(job, outputPath);
		AvroParquetOutputFormat.setSchema(job, schema);
		AvroParquetOutputFormat.setCompression(job, CompressionCodecName.SNAPPY);
		AvroParquetOutputFormat.setCompressOutput(job, true);

		// set a large block size to ensure a single row group.  see discussion
		AvroParquetOutputFormat.setBlockSize(job, 500 * 1024 * 1024);
*/
		
		job.setMapperClass(Bam2GaMapper.class);
		job.setReducerClass(Bam2GaReducer.class);

		return (job.waitForCompletion(true) ? 0 : 1);
	}
}
