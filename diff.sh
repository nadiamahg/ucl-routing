#!/bin/bash
test_name=$1
icdiff ${test_name}Output.txt expected/${test_name}Output.txt
