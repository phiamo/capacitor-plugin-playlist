#import "QueueExceptionGuard.h"

@implementation QueueExceptionGuard

+ (BOOL)performBlock:(void (NS_NOESCAPE ^)(void))block {
    if (block == nil) {
        return NO;
    }
    @try {
        block();
        return YES;
    }
    @catch (NSException *exception) {
        NSLog(
            @"QueueExceptionGuard: %@ — %@",
            exception.name,
            exception.reason ?: @"(no reason)"
        );
        return NO;
    }
}

@end