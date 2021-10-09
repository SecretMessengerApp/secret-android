#
# Makefile
#

PROJECT	  := zproto

PROJDIR		:= $(PWD)

ifeq ($(BUILD_CODE),)
BUILD_CODE := 1
endif

VERSION=$(shell genbuildinfo -b ../BUILDCONTROL --build-version)

all: scala

scala:
	@rm -rf libs/zmessaging-android.*
	@rm -rf ../corelib/zmessaging
	@getcomponents --build-control ../BUILDCONTROL \
		--platform android --component-path ../corelib --force
	@mv ../corelib/zmessaging/zmessaging-android*.jar libs/
