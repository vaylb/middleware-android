#ifndef DATABUFFER_H
#define DATABUFFER_H

#ifdef __cplusplus
extern "C"
{
#endif

#include <sys/types.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

class DataBuffer{
private:
    char * buffer;
    size_t	bufsize;
    volatile size_t write_ptr;
    volatile size_t read_ptr;
public:
    DataBuffer(size_t size);
    ~DataBuffer();
    size_t getReadSpace();
    size_t getWriteSpace();
    void setWritePos(size_t pos);
    size_t setReadPos(size_t pos);
    size_t Read( char *dest, size_t cnt);
    size_t Write( char *stc, size_t cnt);
    void Reset();
    int64_t seek(int offset,int whence);
};

#ifdef __cplusplus
}
#endif

#endif // DATABUFFER_H
