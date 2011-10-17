# NetLogo matrix extension

This package contains the NetLogo matrix extension, which provides support for a new datatype (mathematical "matrix" objects) and associated operations (like matrix multiplication) in NetLogo. 

## Using

The matrix extension is pre-installed in NetLogo. For instructions on using it in your model, or for more information about NetLogo extensions, see the NetLogo User Manual.

## Building

Use the NETLOGO environment variable to tell the Makefile which NetLogoLite.jar to compile against.  For example:

    NETLOGO=/Applications/NetLogo\\\ 5.0 make

If compilation succeeds, `matrix.jar` will be created.

## Credits

The matrix extension was originally written by Forrest Stonedahl, with significant contributions from Charles Staelin (in particular, the forecast & regression primitives).

The matrix extension uses (and is distributed with) the Jama matrix library (Jama-1.0.2.jar, http://math.nist.gov/javanumerics/jama/), which has been released into the public domain.

## Terms of Use

[![CC0](http://i.creativecommons.org/p/zero/1.0/88x31.png)](http://creativecommons.org/publicdomain/zero/1.0/)

The NetLogo matrix extension is in the public domain.  To the extent possible under law, Uri Wilensky has waived all copyright and related or neighboring rights.
