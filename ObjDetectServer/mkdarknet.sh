#!/bin/sh

# Ensure you have a symlink to your CUDA installation in /usr/local/cuda

cd darknet
make GPU=1 OPENCV=1
cd ..
