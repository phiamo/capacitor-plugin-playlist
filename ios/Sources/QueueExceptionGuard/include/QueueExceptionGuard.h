#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface QueueExceptionGuard : NSObject

+ (BOOL)performBlock:(void (NS_NOESCAPE ^)(void))block;

@end

NS_ASSUME_NONNULL_END