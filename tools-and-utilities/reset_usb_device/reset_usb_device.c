/************************************************************************************************
 * This file is part of SerialPundit project and software.
 * 
 * Copyright (C) 2014-2016, Rishi Gupta. All rights reserved.
 *
 * The SerialPundit software is DUAL licensed. It is made available under the terms of the GNU Affero 
 * General Public License (AGPL) v3.0 for non-commercial use and under the terms of a commercial 
 * license for commercial use of this software. 
 * 
 * The SerialPundit software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 ************************************************************************************************/
 
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

int main(int argc, char **argv) {
	const char *filename;
	int fd;
	filename = argv[1];
	fd = open(filename, O_WRONLY);
	errno = 0;
	if(fd < 0) {
		fprintf(stderr, "failed with error code : %d\n", errno);
		return 0;
	}
	ioctl(fd, USBDEVFS_RESET, 0);
	close(fd);
	return 0;
}
