def two_sum(nums,target):
    left=0
    right=len(nums)-1
    while(left<right):
        if(nums[left]+nums[right]==target):
            return left,right
        elif(nums[left]+nums[right]<target):
            left+=1
        else:
            right-=1
nums=[2,7,11,15]
target=9
result=[two_sum(nums,target)]
print(result)
    
