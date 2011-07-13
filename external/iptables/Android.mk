ifneq ($(TARGET_SIMULATOR),true)
  BUILD_IPTABLES := 1
endif
ifeq ($(BUILD_IPTABLES),1)

LOCAL_PATH:= $(call my-dir)

#
# Build libraries
#

# libxtables

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/include/ \
	$(KERNEL_HEADERS) 

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL
LOCAL_CFLAGS+=-DIPTABLES_VERSION=\"1.4.10\" 
LOCAL_CFLAGS+=-DXTABLES_VERSION=\"1.4.10\" # -DIPT_LIB_DIR=\"$(IPT_LIBDIR)\"
LOCAL_CFLAGS+=-DXTABLES_LIBDIR

LOCAL_SRC_FILES:= \
	xtables.c

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=libxtables

include $(BUILD_STATIC_LIBRARY)



# libip4tc

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(KERNEL_HEADERS) \
	$(LOCAL_PATH)/include/

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL

LOCAL_SRC_FILES:= \
	libiptc/libip4tc.c

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=libip4tc

include $(BUILD_STATIC_LIBRARY)

# libext4

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=libext4

# LOCAL_MODULE_CLASS must be defined before calling $(local-intermediates-dir)
#
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
intermediates := $(call local-intermediates-dir)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/include/ \
	$(KERNEL_HEADERS) \
	$(intermediates)/extensions/

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL
LOCAL_CFLAGS+=-D_INIT=$*_init
LOCAL_CFLAGS+=-DIPTABLES_VERSION=\"1.4.10\"
LOCAL_CFLAGS+=-DXTABLES_VERSION=\"1.4.10\"

PF_EXT_SLIB:=ah addrtype ecn  
PF_EXT_SLIB+=icmp #2mark
PF_EXT_SLIB+=realm 
PF_EXT_SLIB+=ttl unclean DNAT LOG #DSCP ECN
PF_EXT_SLIB+=MASQUERADE MIRROR NETMAP REDIRECT REJECT #MARK
PF_EXT_SLIB+=SAME SNAT ULOG # TOS TCPMSS TTL
PF_EXT_SLIB+=TAG

EXT_FUNC+=$(foreach T,$(PF_EXT_SLIB),ipt_$(T))

NEW_PF_EXT_SLIB:=comment conntrack connmark dscp tcpmss esp
NEW_PF_EXT_SLIB+=hashlimit helper iprange length limit mac multiport
NEW_PF_EXT_SLIB+=owner physdev pkttype policy sctp standard state tcp
NEW_PF_EXT_SLIB+=tos udp CLASSIFY CONNMARK
NEW_PF_EXT_SLIB+=NFQUEUE NOTRACK

EXT_FUNC+=$(foreach N,$(NEW_PF_EXT_SLIB),xt_$(N))

# Generated source: gen_initext4.c
FORCE:
	echo FORCE
GEN_INITEXT:= extensions/gen_initext4.c
LOCAL_GEN_INITEXT:= $(LOCAL_PATH)/$(GEN_INITEXT)
$(LOCAL_GEN_INITEXT): FORCE
	$(LOCAL_PATH)/extensions/create_initext4 "$(EXT_FUNC)" > $@
$(intermediates)/extensions/gen_initext4.o: $(GEN_INITEXT)

LOCAL_SRC_FILES:= \
	$(foreach T,$(PF_EXT_SLIB),extensions/libipt_$(T).c) \
	$(foreach N,$(NEW_PF_EXT_SLIB),extensions/libxt_$(N).c) \
	$(GEN_INITEXT)


LOCAL_STATIC_LIBRARIES := \
	libc

include $(BUILD_STATIC_LIBRARY)

#
# Build iptables
#

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/include/ \
	$(KERNEL_HEADERS)

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL
LOCAL_CFLAGS+=-DIPTABLES_VERSION=\"1.4.10\" 
LOCAL_CFLAGS+=-DXTABLES_VERSION=\"1.4.10\" # -DIPT_LIB_DIR=\"$(IPT_LIBDIR)\"
#LOCAL_CFLAGS+=-DIPT_LIB_DIR=\"$(IPT_LIBDIR)\"

LOCAL_SRC_FILES:= \
	iptables.c \
	iptables-standalone.c \
        xshared.c

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=iptables

LOCAL_STATIC_LIBRARIES := \
	libip4tc \
	libext4  \
        libxtables 

include $(BUILD_EXECUTABLE)

endif
