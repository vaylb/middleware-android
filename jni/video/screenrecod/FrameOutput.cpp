/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "ScreenRecord"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include "FrameOutput.h"

using namespace android;

extern "C" {
	#include "jpeglib.h" 
}
#define COMPRESS_QUALITY 100

typedef long LONG;  
typedef unsigned long DWORD;  
typedef unsigned short WORD;  
  
typedef struct {  
        WORD    bfType;  
        DWORD   bfSize;  
        WORD    bfReserved1;  
        WORD    bfReserved2;  
        DWORD   bfOffBits;  
} BMPFILEHEADER_T;  
  
typedef struct{  
        DWORD      biSize;  
        LONG       biWidth;  
        LONG       biHeight;  
        WORD       biPlanes;  
        WORD       biBitCount;  
        DWORD      biCompression;  
        DWORD      biSizeImage;  
        LONG       biXPelsPerMeter;  
        LONG       biYPelsPerMeter;  
        DWORD      biClrUsed;  
        DWORD      biClrImportant;  
} BMPINFOHEADER_T;  


//----------------------------------------------------------
//JPEG compress

/* The following declarations and 5 functions are jpeg related  functions */  
typedef struct {  
    struct jpeg_destination_mgr pub;  
    JOCTET *buf;  
    size_t bufsize;  
    size_t jpegsize;  
} mem_destination_mgr;  
    
typedef mem_destination_mgr *mem_dest_ptr;  
    
    
void init_destination(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    dest->pub.next_output_byte = dest->buf;  
    dest->pub.free_in_buffer = dest->bufsize;  
    dest->jpegsize = 0;  
}  
    
boolean empty_output_buffer(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    dest->pub.next_output_byte = dest->buf;  
    dest->pub.free_in_buffer = dest->bufsize;  
    
    return FALSE;  
}  
    
void term_destination(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    dest->jpegsize = dest->bufsize - dest->pub.free_in_buffer;  
}  
    
static void jpeg_mem_dest(j_compress_ptr cinfo, JOCTET* buf, size_t bufsize)  
{  
    mem_dest_ptr dest;  
    
    if (cinfo->dest == NULL) {  
        cinfo->dest = (struct jpeg_destination_mgr *)  
            (*cinfo->mem->alloc_small)((j_common_ptr)cinfo, JPOOL_PERMANENT,  
            sizeof(mem_destination_mgr));  
    }  
    
    dest = (mem_dest_ptr) cinfo->dest;  
    
    dest->pub.init_destination    = init_destination;  
    dest->pub.empty_output_buffer = empty_output_buffer;  
    dest->pub.term_destination    = term_destination;  
    
    dest->buf      = buf;  
    dest->bufsize  = bufsize;  
    dest->jpegsize = 0;  
}  
    
static int jpeg_mem_size(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    return dest->jpegsize;  
}
//------------------------- JPEG compress end ---------------------------------


static const bool kShowTiming = false;      // set to "true" for debugging
static const int kGlBytesPerPixel = 4;      // GL_RGBA
static const int kOutBytesPerPixel = 3;     // RGB only

inline void FrameOutput::setValueLE(uint8_t* buf, uint32_t value) {
    // Since we're running on an Android device, we're (almost) guaranteed
    // to be little-endian, and (almost) guaranteed that unaligned 32-bit
    // writes will work without any performance penalty... but do it
    // byte-by-byte anyway.
    buf[0] = (uint8_t) value;
    buf[1] = (uint8_t) (value >> 8);
    buf[2] = (uint8_t) (value >> 16);
    buf[3] = (uint8_t) (value >> 24);
}

status_t FrameOutput::createInputSurface(int width, int height,
        sp<IGraphicBufferProducer>* pBufferProducer) {
    status_t err;

    err = mEglWindow.createPbuffer(width, height);
    if (err != NO_ERROR) {
        return err;
    }
    mEglWindow.makeCurrent();

    glViewport(0, 0, width, height);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    // Shader for rendering the external texture.
    err = mExtTexProgram.setup(Program::PROGRAM_EXTERNAL_TEXTURE);
    if (err != NO_ERROR) {
        return err;
    }

    // Input side (buffers from virtual display).
    glGenTextures(1, &mExtTextureName);
    if (mExtTextureName == 0) {
        ALOGE("glGenTextures failed: %#x", glGetError());
        return UNKNOWN_ERROR;
    }

    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    mGlConsumer = new GLConsumer(consumer, mExtTextureName,
                GL_TEXTURE_EXTERNAL_OES, true, false);
    mGlConsumer->setName(String8("virtual display"));
    mGlConsumer->setDefaultBufferSize(width, height);
    mGlConsumer->setDefaultMaxBufferCount(5);
    mGlConsumer->setConsumerUsageBits(GRALLOC_USAGE_HW_TEXTURE);

    mGlConsumer->setFrameAvailableListener(this);
	//vaylb changed in if{}	
	if(mPixelBuf == NULL){
		mPixelBuf = new uint8_t[width * height * kGlBytesPerPixel];
	}
	
    *pBufferProducer = producer;

	savergb = true;
	savecount = 0;

    ALOGD("vaylb-->FrameOutput::createInputSurface OK,width = %d, height = %d",width, height);
    return NO_ERROR;
}

status_t FrameOutput::copyFrame(FILE* fp, long timeoutUsec, bool rawFrames) {
    Mutex::Autolock _l(mMutex);
    ALOGE("vaylb-->copyFrame %ld\n", timeoutUsec);

    if (!mFrameAvailable) {
        nsecs_t timeoutNsec = (nsecs_t)timeoutUsec * 1000;
        int cc = mEventCond.waitRelative(mMutex, timeoutNsec);
        if (cc == -ETIMEDOUT) {
            ALOGV("cond wait timed out");
            return ETIMEDOUT;
        } else if (cc != 0) {
            ALOGW("cond wait returned error %d", cc);
            return cc;
        }
    }
    if (!mFrameAvailable) {
        // This happens when Ctrl-C is hit.  Apparently POSIX says that the
        // pthread wait call doesn't return EINTR, treating this instead as
        // an instance of a "spurious wakeup".  We didn't get a frame, so
        // we just treat it as a timeout.
        return ETIMEDOUT;
    }

    // A frame is available.  Clear the flag for the next round.
    mFrameAvailable = false;

    float texMatrix[16];
    mGlConsumer->updateTexImage();
    mGlConsumer->getTransformMatrix(texMatrix);

    // The data is in an external texture, so we need to render it to the
    // pbuffer to get access to RGB pixel data.  We also want to flip it
    // upside-down for easy conversion to a bitmap.
    int width = mEglWindow.getWidth();
    int height = mEglWindow.getHeight();
    status_t err = mExtTexProgram.blit(mExtTextureName, texMatrix, 0, 0,
            width, height, true);
    if (err != NO_ERROR) {
        return err;
    }

    // GLES only guarantees that glReadPixels() will work with GL_RGBA, so we
    // need to get 4 bytes/pixel and reduce it.  Depending on the size of the
    // screen and the device capabilities, this can take a while.
    int64_t startWhenNsec, pixWhenNsec, endWhenNsec;
    if (kShowTiming) {
        startWhenNsec = systemTime(CLOCK_MONOTONIC);
    }
    GLenum glErr;
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, mPixelBuf);
    if ((glErr = glGetError()) != GL_NO_ERROR) {
        ALOGE("glReadPixels failed: %#x", glErr);
        return UNKNOWN_ERROR;
    }
    if (kShowTiming) {
        pixWhenNsec = systemTime(CLOCK_MONOTONIC);
    }
    reduceRgbaToRgb(mPixelBuf, width * height);
    if (kShowTiming) {
        endWhenNsec = systemTime(CLOCK_MONOTONIC);
        ALOGD("got pixels (get=%.3f ms, reduce=%.3fms)",
                (pixWhenNsec - startWhenNsec) / 1000000.0,
                (endWhenNsec - pixWhenNsec) / 1000000.0);
    }

    size_t rgbDataLen = width * height * kOutBytesPerPixel;

    if (!rawFrames) {
        // Fill out the header.
        size_t headerLen = sizeof(uint32_t) * 5;
        size_t packetLen = headerLen - sizeof(uint32_t) + rgbDataLen;
        uint8_t header[headerLen];
        setValueLE(&header[0], packetLen);
        setValueLE(&header[4], width);
        setValueLE(&header[8], height);
        setValueLE(&header[12], width * kOutBytesPerPixel);
        setValueLE(&header[16], HAL_PIXEL_FORMAT_RGB_888);
        fwrite(header, 1, headerLen, fp);
    }

    // Currently using buffered I/O rather than writev().  Not expecting it
    // to make much of a difference, but it might be worth a test for larger
    // frame sizes.
    if (kShowTiming) {
        startWhenNsec = systemTime(CLOCK_MONOTONIC);
    }
    fwrite(mPixelBuf, 1, rgbDataLen, fp);
    fflush(fp);
    if (kShowTiming) {
        endWhenNsec = systemTime(CLOCK_MONOTONIC);
        ALOGD("wrote pixels (%.3f ms)",
                (endWhenNsec - startWhenNsec) / 1000000.0);
    }

    if (ferror(fp)) {
        // errno may not be useful; log it anyway
        ALOGE("write failed (errno=%d)", errno);
        return UNKNOWN_ERROR;
    }

    return NO_ERROR;
}

int FrameOutput::compressFrame(uint8_t* outbuf, long timeoutUsec){
	Mutex::Autolock _l(mMutex);

    if (!mFrameAvailable) {
        nsecs_t timeoutNsec = (nsecs_t)timeoutUsec * 1000;
        int cc = mEventCond.waitRelative(mMutex, timeoutNsec);
        if (cc == -ETIMEDOUT) {
            ALOGV("cond wait timed out");
            return ETIMEDOUT;
        } else if (cc != 0) {
            ALOGW("cond wait returned error %d", cc);
            return cc;
        }
    }
    if (!mFrameAvailable) {
        return ETIMEDOUT;
    }

    // A frame is available.  Clear the flag for the next round.
    mFrameAvailable = false;
	
	float texMatrix[16];
	mGlConsumer->updateTexImage();
	mGlConsumer->getTransformMatrix(texMatrix);

	// The data is in an external texture, so we need to render it to the
	// pbuffer to get access to RGB pixel data.  We also want to flip it
	// upside-down for easy conversion to a bitmap.
	int width = mEglWindow.getWidth();
	int height = mEglWindow.getHeight();
	status_t err = mExtTexProgram.blit(mExtTextureName, texMatrix, 0, 0,
			width, height, true);
	if (err != NO_ERROR) {
		return err;
	}

	// GLES only guarantees that glReadPixels() will work with GL_RGBA, so we
	// need to get 4 bytes/pixel and reduce it.  Depending on the size of the
	// screen and the device capabilities, this can take a while.

	GLenum glErr;
	glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, mPixelBuf);
	if ((glErr = glGetError()) != GL_NO_ERROR) {
		ALOGE("glReadPixels failed: %#x", glErr);
		return UNKNOWN_ERROR;
	}

	reduceRgbaToRgb(mPixelBuf, width * height);
	//size_t rgbDataLen = width * height * kOutBytesPerPixel;
	if(width>height && savergb){
		savecount++;
		if(savecount == 30){
			savebmp(mPixelBuf,"/sdcard/testpic12.bmp",width,height);
			savergb = false;
		}
	}

	/*
	bool rawFrames = false;
	if (!rawFrames) {
        // Fill out the header.
        FILE* fp = fopen("/sdcard/testpic.bmp", "w");
        
		fwrite(mPixelBuf, 1, rgbDataLen, fp);
    	fflush(fp);
		fclose(fp);
		rawFrames = true;
    	}
    	*/
    

	//fwrite(mPixelBuf, 1, rgbDataLen, fp);
	//fflush(fp);
	//return compressRGBToJPEG(outbuf, width, height);

	return compressRGBToJPEG(outbuf, width, height);

}
int FrameOutput::compressRGBToJPEG(uint8_t* outbuf, int width, int height){
	struct jpeg_compress_struct cinfo;
	struct jpeg_error_mgr jerr;

	/* this is a pointer to one row of image data 
	FILE *outfile = fopen("/sdcard/testpic.jpg", "wb" );
	if(outfile == NULL){
		ALOGE("vaylb-->compressRGBAToJPEG open file error");
		return -1;
	}
	*/

	cinfo.err = jpeg_std_error( &jerr );
	jpeg_create_compress(&cinfo);
	//jpeg_stdio_dest(&cinfo, outfile);

	/* Setting the parameters of the output file here */
	cinfo.image_width = width;//width;
	cinfo.image_height = height;//width;
	cinfo.input_components = 3;
	cinfo.in_color_space = JCS_RGB;

	jpeg_set_defaults( &cinfo );
	size_t mem_size = width*height*3;
	jpeg_mem_dest(&cinfo, outbuf, mem_size);
	/* Now do the compression .. */
	jpeg_start_compress( &cinfo, TRUE );

	JSAMPROW buffer;
	//ALOGE("vaylb-->compressRGBAToJPEG width = %d, height = %d", width, height);
	
	while (cinfo.next_scanline < cinfo.image_height) {
        JSAMPLE *row = mPixelBuf + 3 * cinfo.image_width * cinfo.next_scanline;
        jpeg_write_scanlines(&cinfo, &row, 1);
    }
	jpeg_finish_compress( &cinfo );
	int jpeg_image_size = jpeg_mem_size(&cinfo);
	jpeg_destroy_compress( &cinfo );
	//fclose( outfile );
	return jpeg_image_size;

}


int FrameOutput::compressRGBAToJPEG(uint8_t* outbuf, int width, int height){
	struct jpeg_compress_struct cinfo;
	struct jpeg_error_mgr jerr;

	/* this is a pointer to one row of image data 
	FILE *outfile = fopen("/sdcard/testpic.jpg", "wb" );
	if(outfile == NULL){
		ALOGE("vaylb-->compressRGBAToJPEG open file error");
		return -1;
	}
	*/

	cinfo.err = jpeg_std_error( &jerr );
	jpeg_create_compress(&cinfo);
	//jpeg_stdio_dest(&cinfo, outfile);

	/* Setting the parameters of the output file here */
	cinfo.image_width = width;//width;
	cinfo.image_height = height;//width;
	cinfo.input_components = 4;
	cinfo.in_color_space = JCS_RGBA_8888;

	jpeg_set_defaults( &cinfo );
	size_t mem_size = width*height*3;
	jpeg_mem_dest(&cinfo, outbuf, mem_size);
	/* Now do the compression .. */
	jpeg_start_compress( &cinfo, TRUE );

	JSAMPROW buffer;
	ALOGE("vaylb-->compressRGBAToJPEG width = %d, height = %d", width, height);
	
	while (cinfo.next_scanline < cinfo.image_height) {
        JSAMPLE *row = mPixelBuf + 4 * cinfo.image_width * cinfo.next_scanline;
        jpeg_write_scanlines(&cinfo, &row, 1);
    }
	jpeg_finish_compress( &cinfo );
	int jpeg_image_size = jpeg_mem_size(&cinfo);
	jpeg_destroy_compress( &cinfo );
	//fclose( outfile );
	return jpeg_image_size;

}

void FrameOutput::reduceRgbaToRgb(uint8_t* buf, unsigned int pixelCount) {
    // Convert RGBA to RGB.
    //
    // Unaligned 32-bit accesses are allowed on ARM, so we could do this
    // with 32-bit copies advancing at different rates (taking care at the
    // end to not go one byte over).
    const uint8_t* readPtr = buf;
    for (unsigned int i = 0; i < pixelCount; i++) {
        *buf++ = *readPtr++;
        *buf++ = *readPtr++;
        *buf++ = *readPtr++;
        readPtr++;
    }
}

// Callback; executes on arbitrary thread.
void FrameOutput::onFrameAvailable(const BufferItem& /* item */) {
    Mutex::Autolock _l(mMutex);
    mFrameAvailable = true;
    mEventCond.signal();
}
  
void FrameOutput::savebmp(uint8_t* pdata, char * bmp_file, int width, int height )  
{      
       int size = width*height*3*sizeof(char);
       BMPFILEHEADER_T bfh;  
       bfh.bfType = (WORD)0x4d42;  //bm  
       bfh.bfSize = size  // data size  
              + sizeof( BMPFILEHEADER_T ) // first section size  
              + sizeof( BMPINFOHEADER_T ) // second section size  
              ;  
       bfh.bfReserved1 = 0; // reserved  
       bfh.bfReserved2 = 0; // reserved  
       bfh.bfOffBits = sizeof( BMPFILEHEADER_T )+ sizeof( BMPINFOHEADER_T );
       BMPINFOHEADER_T bih;  
       bih.biSize = sizeof(BMPINFOHEADER_T);  
       bih.biWidth = width;  
       bih.biHeight = -height;
       bih.biPlanes = 1;
       bih.biBitCount = 24;  
       bih.biCompression = 0;
       bih.biSizeImage = size;  
       bih.biXPelsPerMeter = 2835 ;
       bih.biYPelsPerMeter = 2835 ;  
       bih.biClrUsed = 0;
       bih.biClrImportant = 0;
       FILE * fp = fopen( bmp_file,"wb" );  
       if( !fp ) return;  
  
       fwrite( &bfh, 8, 1,  fp );
       fwrite(&bfh.bfReserved2, sizeof(bfh.bfReserved2), 1, fp);  
       fwrite(&bfh.bfOffBits, sizeof(bfh.bfOffBits), 1, fp);  
       fwrite( &bih, sizeof(BMPINFOHEADER_T),1,fp );  
       fwrite(pdata,size,1,fp);  
       fclose( fp );  
}

