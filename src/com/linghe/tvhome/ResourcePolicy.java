package com.linghe.tvhome;

/** Decode bounds, not file compression size, determine bitmap allocation. */
final class ResourcePolicy {
 static int sampleSize(int width,int height,int targetWidth,int targetHeight){
  if(width<1||height<1||targetWidth<1||targetHeight<1) return 1;
  int sample=1;
  while(sample<(1<<30)&&((long)width> (long)targetWidth*sample || (long)height>(long)targetHeight*sample))sample*=2;
  return sample;
 }
 static final int ICON_CACHE_BYTES=2*1024*1024;
 static int iconPixels(int dpPixels){return Math.max(1,Math.min(dpPixels,128));}
}
